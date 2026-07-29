from __future__ import annotations

from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from html import unescape
from typing import Iterable
from xml.etree import ElementTree

import requests
from bs4 import BeautifulSoup

from ..contracts import CompanyTarget, ProviderArticle
from ..dedup import normalize_url
from .base import ProviderFetchResult, ProviderFetchStatus


class GoogleNewsRssProvider:
    """Google News RSS의 공개 검색 피드를 읽는 forward 수집기.

    Google RSS는 완전한 역사 archive나 pagination 계약을 제공하지 않으므로
    이 구현은 최근 뉴스의 best-effort 수집에만 사용합니다.
    """

    name = "google_news_rss"
    DEFAULT_ENDPOINT = "https://news.google.com/rss/search"
    _ALLOWED_XML_CONTENT_TYPES = (
        "application/atom+xml",
        "application/rss+xml",
        "application/xml",
        "text/xml",
    )

    def __init__(
        self,
        *,
        session: requests.Session | None = None,
        endpoint: str = DEFAULT_ENDPOINT,
        language: str = "en-US",
        country: str = "US",
        edition: str = "US:en",
        connect_timeout_seconds: float = 3.05,
        read_timeout_seconds: float = 10.0,
        max_response_bytes: int = 2_000_000,
        max_feed_items: int = 500,
        max_snippet_chars: int = 2_000,
        saturation_item_threshold: int = 100,
    ) -> None:
        self.session = session or requests.Session()
        self.endpoint = endpoint
        self.language = language
        self.country = country
        self.edition = edition
        self.timeout = (connect_timeout_seconds, read_timeout_seconds)
        self.max_response_bytes = max_response_bytes
        self.max_feed_items = max_feed_items
        self.max_snippet_chars = max_snippet_chars
        self.saturation_item_threshold = saturation_item_threshold
        if (
            connect_timeout_seconds <= 0
            or read_timeout_seconds <= 0
            or self.max_response_bytes <= 0
            or self.max_feed_items <= 0
            or self.max_snippet_chars <= 0
            or self.saturation_item_threshold <= 0
        ):
            raise ValueError("timeout/응답/feed item/snippet 제한은 0보다 커야 합니다.")

    @staticmethod
    def build_query(company: CompanyTarget) -> str:
        """회사명/별칭/티커를 한 요청의 OR 검색식으로 합칩니다."""

        terms: list[str] = []
        for name in (company.legal_name, *company.aliases):
            cleaned = " ".join(name.split()).strip('"')
            if cleaned:
                terms.append(f'"{cleaned}"')

        for ticker in company.tickers:
            symbol = ticker.upper()
            # 검색은 요구사항대로 ticker+stock을 사용하되, 모호 ticker 단독 결과는
            # relevance 단계에서 회사명/승인 별칭이 없으면 제외합니다.
            terms.append(f'"{symbol} stock"')

        # 설정 파일의 중복 alias 때문에 같은 검색어가 반복되지 않도록 순서를 보존합니다.
        return " OR ".join(dict.fromkeys(terms))

    def fetch_company_news(
        self,
        company: CompanyTarget,
        start_at: datetime,
        end_at: datetime,
        limit: int | None = None,
    ) -> ProviderFetchResult:
        start_utc = _require_aware_utc(start_at, "start_at")
        end_utc = _require_aware_utc(end_at, "end_at")
        if start_utc > end_utc:
            raise ValueError("start_at은 end_at보다 늦을 수 없습니다.")

        query = self.build_query(company)
        if not query:
            return ProviderFetchResult(
                status=ProviderFetchStatus.ERROR,
                query_count=0,
                errors=(
                    f"{company.company_key}: 검색 가능한 회사명 또는 ticker가 없습니다.",
                ),
            )

        try:
            response = self.session.get(
                self.endpoint,
                params={
                    "q": query,
                    "hl": self.language,
                    "gl": self.country,
                    "ceid": self.edition,
                },
                headers={"User-Agent": "sisc-web-event-news-context/1.0"},
                timeout=self.timeout,
                allow_redirects=False,
                stream=True,
            )
            response.raise_for_status()
            content = self._read_limited_response(response)
            parsed, raw_count, invalid_count = self._parse_feed(
                content,
                start_at=start_utc,
                end_at=end_utc,
                limit=limit,
            )
        except (requests.RequestException, ValueError, ElementTree.ParseError) as exc:
            return ProviderFetchResult(
                status=ProviderFetchStatus.ERROR,
                failure_reason=_provider_failure_reason(exc),
                query_count=1,
                errors=(f"{type(exc).__name__}: {exc}",),
            )
        finally:
            if "response" in locals():
                close = getattr(response, "close", None)
                if callable(close):
                    close()

        if not parsed:
            if raw_count and invalid_count == raw_count:
                return ProviderFetchResult(
                    status=ProviderFetchStatus.ERROR,
                    failure_reason="invalid_feed",
                    query_count=1,
                    raw_item_count=raw_count,
                    invalid_item_count=invalid_count,
                    feed_saturated=raw_count >= self.saturation_item_threshold,
                    errors=(
                        "invalid_feed: RSS item이 모두 필수 필드/시각 검증에 실패했습니다.",
                    ),
                )
            return ProviderFetchResult(
                status=ProviderFetchStatus.NO_RESULTS,
                query_count=1,
                raw_item_count=raw_count,
                invalid_item_count=invalid_count,
                feed_saturated=raw_count >= self.saturation_item_threshold,
            )

        oldest = min(article.published_at_utc for article in parsed)
        return ProviderFetchResult(
            status=ProviderFetchStatus.SUCCESS,
            articles=tuple(parsed),
            query_count=1,
            raw_item_count=raw_count,
            invalid_item_count=invalid_count,
            feed_saturated=raw_count >= self.saturation_item_threshold,
            oldest_item_published_at=oldest,
        )

    def _read_limited_response(self, response: requests.Response) -> bytes:
        declared_length = response.headers.get("Content-Length")
        if declared_length:
            try:
                declared_bytes = int(declared_length)
            except ValueError:
                declared_bytes = 0
            if declared_bytes > self.max_response_bytes:
                raise ValueError(
                    "RSS 응답의 Content-Length가 허용 크기를 초과했습니다."
                )

        content_type = (
            response.headers.get("Content-Type", "").lower().split(";", 1)[0].strip()
        )
        if content_type and content_type not in self._ALLOWED_XML_CONTENT_TYPES:
            raise ValueError(f"RSS가 아닌 Content-Type입니다: {content_type}")

        chunks: list[bytes] = []
        received_bytes = 0
        for chunk in response.iter_content(chunk_size=64 * 1024):
            if not chunk:
                continue
            received_bytes += len(chunk)
            if received_bytes > self.max_response_bytes:
                raise ValueError("RSS 응답 크기가 허용 범위를 초과했습니다.")
            chunks.append(chunk)
        content = b"".join(chunks)

        lowered = content.lower()
        if b"<!doctype" in lowered or b"<!entity" in lowered:
            raise ValueError("DTD/ENTITY가 포함된 XML 응답은 처리하지 않습니다.")
        return content

    def _parse_feed(
        self,
        content: bytes,
        *,
        start_at: datetime,
        end_at: datetime,
        limit: int | None,
    ) -> tuple[list[ProviderArticle], int, int]:
        root = ElementTree.fromstring(content)
        items = list(_iter_by_local_name(root, "item"))
        if len(items) > self.max_feed_items:
            raise ValueError(
                f"RSS item 수가 허용 범위({self.max_feed_items})를 초과했습니다."
            )
        articles: list[ProviderArticle] = []
        invalid_count = 0

        for item in items:
            title = _child_text(item, "title")
            link = _child_text(item, "link")
            published_raw = _child_text(item, "pubDate")
            if (
                not title
                or not link
                or normalize_url(link) is None
                or not published_raw
            ):
                invalid_count += 1
                continue

            try:
                published_at = parsedate_to_datetime(published_raw)
                if published_at is None or published_at.tzinfo is None:
                    raise ValueError("timezone이 없는 pubDate")
                published_at_utc = published_at.astimezone(timezone.utc)
            except (TypeError, ValueError, OverflowError):
                invalid_count += 1
                continue

            if published_at_utc < start_at or published_at_utc > end_at:
                continue

            source = _child_text(item, "source") or None
            guid = _child_text(item, "guid") or None
            description = _child_text(item, "description")
            snippet = (
                _description_to_text(description, self.max_snippet_chars)
                if description
                else None
            )

            articles.append(
                ProviderArticle(
                    provider=self.name,
                    provider_article_id=guid,
                    title=unescape(title).strip(),
                    provider_url=link.strip(),
                    published_at_raw=published_raw.strip(),
                    published_at_utc=published_at_utc,
                    source=unescape(source).strip() if source else None,
                    snippet=snippet,
                )
            )
            if limit is not None and len(articles) >= limit:
                break

        return articles, len(items), invalid_count


def _require_aware_utc(value: datetime, field_name: str) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field_name}은 timezone-aware datetime이어야 합니다.")
    return value.astimezone(timezone.utc)


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _iter_by_local_name(
    root: ElementTree.Element, name: str
) -> Iterable[ElementTree.Element]:
    return (element for element in root.iter() if _local_name(element.tag) == name)


def _child_text(parent: ElementTree.Element, name: str) -> str:
    for child in parent:
        if _local_name(child.tag) == name:
            return "".join(child.itertext()).strip()
    return ""


def _description_to_text(value: str, max_chars: int) -> str | None:
    # description은 공급자가 제공한 snippet일 뿐, 새로 생성한 요약이 아닙니다.
    text = BeautifulSoup(unescape(value), "html.parser").get_text(" ", strip=True)
    normalized = " ".join(text.split())
    return normalized[:max_chars] or None


def _provider_failure_reason(exc: Exception) -> str:
    if isinstance(exc, requests.Timeout):
        return "source_timeout"
    if isinstance(exc, requests.HTTPError):
        status_code = getattr(exc.response, "status_code", None)
        return f"source_http_{status_code}" if status_code else "source_http_error"
    if isinstance(exc, requests.ConnectionError):
        return "source_connection_error"
    if isinstance(exc, requests.RequestException):
        return "source_request_error"
    if isinstance(exc, ElementTree.ParseError):
        return "invalid_xml"
    return "invalid_payload"
