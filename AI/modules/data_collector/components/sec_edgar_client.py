from __future__ import annotations

import base64
import hashlib
import json
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path
from typing import Callable

import requests


RETRYABLE_STATUS_CODES = {403, 429, 500, 502, 503, 504}


@dataclass(frozen=True)
class SecHttpResponse:
    """테스트와 캐시 사용을 쉽게 하기 위한 최소 HTTP 응답 객체입니다."""

    url: str
    status_code: int
    content: bytes
    headers: dict[str, str]

    @property
    def text(self) -> str:
        encoding = "utf-8"
        content_type = self.headers.get("content-type", "")
        if "charset=" in content_type:
            encoding = content_type.split("charset=", 1)[1].split(";", 1)[0].strip()
        return self.content.decode(encoding, errors="replace")

    def json(self):
        return json.loads(self.text)


class SecRateLimiter:
    """여러 스레드가 공유해도 SEC 전체 요청률을 넘지 않게 직렬화합니다."""

    def __init__(
        self,
        max_requests_per_second: float = 8.0,
        *,
        clock: Callable[[], float] = time.monotonic,
        sleeper: Callable[[float], None] = time.sleep,
    ):
        if not 0 < max_requests_per_second <= 10:
            raise ValueError("SEC 요청 속도는 초당 0회 초과 10회 이하여야 합니다.")
        self._minimum_interval = 1.0 / max_requests_per_second
        self._clock = clock
        self._sleeper = sleeper
        self._last_request_at: float | None = None
        self._lock = threading.Lock()

    def wait(self) -> None:
        with self._lock:
            now = self._clock()
            if self._last_request_at is not None:
                remaining = self._minimum_interval - (now - self._last_request_at)
                if remaining > 0:
                    self._sleeper(remaining)
                    now = self._clock()
            self._last_request_at = now


class SecFileCache:
    """URL별 응답을 저장하는 간단한 파일 캐시입니다."""

    def __init__(self, cache_dir: Path, ttl_seconds: int = 86_400):
        self.cache_dir = cache_dir
        self.ttl_seconds = ttl_seconds
        self.cache_dir.mkdir(parents=True, exist_ok=True)

    def _path_for(self, url: str) -> Path:
        key = hashlib.sha256(url.encode("utf-8")).hexdigest()
        return self.cache_dir / f"{key}.json"

    def get(self, url: str, *, immutable: bool = False) -> SecHttpResponse | None:
        path = self._path_for(url)
        if not path.exists():
            return None
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
            fetched_at = datetime.fromisoformat(payload["fetched_at"])
            age = (datetime.now(timezone.utc) - fetched_at).total_seconds()
            if not immutable and age > self.ttl_seconds:
                return None
            return SecHttpResponse(
                url=url,
                status_code=int(payload["status_code"]),
                content=base64.b64decode(payload["body"]),
                headers={str(k).lower(): str(v) for k, v in payload["headers"].items()},
            )
        except (OSError, ValueError, KeyError):
            return None

    def put(self, response: SecHttpResponse) -> None:
        payload = {
            "url": response.url,
            "fetched_at": datetime.now(timezone.utc).isoformat(),
            "status_code": response.status_code,
            "headers": response.headers,
            "body": base64.b64encode(response.content).decode("ascii"),
        }
        self._path_for(response.url).write_text(
            json.dumps(payload, ensure_ascii=False),
            encoding="utf-8",
        )


class SecEdgarClient:
    """공정 접근 정책을 준수하는 SEC EDGAR HTTP 클라이언트입니다."""

    def __init__(
        self,
        user_agent: str,
        *,
        cache_dir: Path,
        max_requests_per_second: float = 8.0,
        timeout_seconds: float = 20.0,
        max_retries: int = 3,
        cache_ttl_seconds: int = 86_400,
        session: requests.Session | None = None,
        sleeper: Callable[[float], None] = time.sleep,
    ):
        if not user_agent or "@" not in user_agent:
            raise ValueError(
                "SEC_USER_AGENT에는 서비스명과 연락 가능한 이메일을 포함해야 합니다. "
                "예: 'SISC Event Alpha Lab contact@example.com'"
            )
        self.user_agent = user_agent
        self.timeout_seconds = timeout_seconds
        self.max_retries = max_retries
        self.session = session or requests.Session()
        self.sleeper = sleeper
        self.rate_limiter = SecRateLimiter(
            max_requests_per_second=max_requests_per_second,
            sleeper=sleeper,
        )
        self.cache = SecFileCache(cache_dir, ttl_seconds=cache_ttl_seconds)

    def get(self, url: str, *, immutable: bool = False) -> SecHttpResponse:
        cached = self.cache.get(url, immutable=immutable)
        if cached is not None:
            return cached

        headers = {
            "User-Agent": self.user_agent,
            "Accept-Encoding": "gzip, deflate",
            "Accept": "application/json, text/html, application/xml, text/xml, */*",
        }
        last_error: Exception | None = None

        for attempt in range(self.max_retries + 1):
            self.rate_limiter.wait()
            try:
                raw = self.session.get(url, headers=headers, timeout=self.timeout_seconds)
                response = SecHttpResponse(
                    url=str(getattr(raw, "url", url)),
                    status_code=int(raw.status_code),
                    content=bytes(raw.content),
                    headers={str(k).lower(): str(v) for k, v in raw.headers.items()},
                )
            except requests.RequestException as exc:
                last_error = exc
                if attempt >= self.max_retries:
                    raise
                self.sleeper(min(2**attempt, 30))
                continue

            if 200 <= response.status_code < 300:
                self.cache.put(response)
                return response

            if response.status_code not in RETRYABLE_STATUS_CODES or attempt >= self.max_retries:
                raise requests.HTTPError(
                    f"SEC 요청 실패: status={response.status_code}, url={url}"
                )

            self.sleeper(self._retry_delay(response, attempt))

        raise RuntimeError(f"SEC 요청을 완료하지 못했습니다: {last_error}")

    @staticmethod
    def _retry_delay(response: SecHttpResponse, attempt: int) -> float:
        retry_after = response.headers.get("retry-after")
        if retry_after:
            try:
                return min(float(retry_after), 60.0)
            except ValueError:
                try:
                    retry_at = parsedate_to_datetime(retry_after)
                    now = datetime.now(retry_at.tzinfo or timezone.utc)
                    return max(0.0, min((retry_at - now).total_seconds(), 60.0))
                except (TypeError, ValueError):
                    pass
        return float(min(2**attempt, 30))

    def get_json(self, url: str, *, immutable: bool = False):
        return self.get(url, immutable=immutable).json()
