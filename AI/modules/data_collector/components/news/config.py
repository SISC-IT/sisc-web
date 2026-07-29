from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from .contracts import CompanyTarget


PROJECT_ROOT = Path(__file__).resolve().parents[5]
DEFAULT_CONFIG_PATH = (
    PROJECT_ROOT / "AI/modules/data_collector/config/news_collection.json"
)


@dataclass(frozen=True)
class NewsCollectionConfig:
    """독립 기업 뉴스 one-shot 수집 설정."""

    universe_file: Path
    provider_name: str = "google_news_rss"
    language: str = "en-US"
    country: str = "US"
    edition: str = "US:en"
    connect_timeout_seconds: float = 3.05
    read_timeout_seconds: float = 10.0
    request_interval_seconds: float = 0.25
    max_response_bytes: int = 2_000_000
    default_lookback_hours: int = 2
    max_recovery_hours: int = 72
    per_company_limit: int = 100
    company_relevance_threshold: float = 0.60

    @classmethod
    def from_file(
        cls, path: str | Path = DEFAULT_CONFIG_PATH
    ) -> "NewsCollectionConfig":
        config_path = _resolve_project_path(path)
        with config_path.open("r", encoding="utf-8") as config_file:
            raw = json.load(config_file)

        provider = raw.get("provider", {})
        collection = raw.get("collection", {})
        universe_file = raw.get("universe_file")
        if not universe_file:
            raise ValueError("news_collection.json에 universe_file이 필요합니다.")

        config = cls(
            universe_file=_resolve_project_path(universe_file),
            provider_name=str(provider.get("name", cls.provider_name)),
            language=str(provider.get("language", cls.language)),
            country=str(provider.get("country", cls.country)),
            edition=str(provider.get("edition", cls.edition)),
            connect_timeout_seconds=float(
                provider.get("connect_timeout_seconds", cls.connect_timeout_seconds)
            ),
            read_timeout_seconds=float(
                provider.get("read_timeout_seconds", cls.read_timeout_seconds)
            ),
            request_interval_seconds=float(
                provider.get("request_interval_seconds", cls.request_interval_seconds)
            ),
            max_response_bytes=int(
                provider.get("max_response_bytes", cls.max_response_bytes)
            ),
            default_lookback_hours=int(
                collection.get("default_lookback_hours", cls.default_lookback_hours)
            ),
            max_recovery_hours=int(
                collection.get("max_recovery_hours", cls.max_recovery_hours)
            ),
            per_company_limit=int(
                collection.get("per_company_limit", cls.per_company_limit)
            ),
            company_relevance_threshold=float(
                collection.get(
                    "company_relevance_threshold",
                    cls.company_relevance_threshold,
                )
            ),
        )
        config.validate()
        return config

    def validate(self) -> None:
        if self.provider_name != "google_news_rss":
            raise ValueError(
                "현재 구현된 provider는 google_news_rss뿐입니다. "
                "Infomax는 API 계약 확인 후 adapter를 추가해야 합니다."
            )
        if self.request_interval_seconds < 0:
            raise ValueError("request_interval_seconds는 음수일 수 없습니다.")
        if self.connect_timeout_seconds <= 0 or self.read_timeout_seconds <= 0:
            raise ValueError("provider timeout은 0보다 커야 합니다.")
        if self.max_response_bytes <= 0:
            raise ValueError("max_response_bytes는 0보다 커야 합니다.")
        if self.default_lookback_hours <= 0:
            raise ValueError("default_lookback_hours는 0보다 커야 합니다.")
        if self.max_recovery_hours < self.default_lookback_hours:
            raise ValueError(
                "max_recovery_hours는 default_lookback_hours 이상이어야 합니다."
            )
        if self.per_company_limit <= 0:
            raise ValueError("per_company_limit은 0보다 커야 합니다.")
        if not 0 <= self.company_relevance_threshold <= 1:
            raise ValueError("company_relevance_threshold는 0과 1 사이여야 합니다.")


@dataclass(frozen=True)
class CompanyUniverse:
    universe_name: str
    universe_mode: str
    as_of: str
    source_url: str
    companies: tuple[CompanyTarget, ...]


def load_company_universe(path: str | Path) -> CompanyUniverse:
    universe_path = _resolve_project_path(path)
    with universe_path.open("r", encoding="utf-8") as universe_file:
        raw = json.load(universe_file)

    required_metadata = ("universe_name", "universe_mode", "as_of", "source_url")
    missing = [key for key in required_metadata if not raw.get(key)]
    if missing:
        raise ValueError(f"universe metadata 누락: {', '.join(missing)}")

    companies: list[CompanyTarget] = []
    company_keys: set[str] = set()
    seen_tickers: dict[str, str] = {}
    for item in raw.get("companies", []):
        company = CompanyTarget(
            company_key=str(item["company_key"]),
            cik=str(item["cik"]).zfill(10) if item.get("cik") else None,
            legal_name=str(item["legal_name"]),
            tickers=tuple(str(value).upper() for value in item.get("tickers", [])),
            aliases=tuple(str(value) for value in item.get("aliases", [])),
            enabled=item.get("enabled", True),
            ambiguous_tickers=tuple(
                str(value).upper() for value in item.get("ambiguous_tickers", [])
            ),
        )
        if company.company_key in company_keys:
            raise ValueError(f"중복 company_key: {company.company_key}")
        company_keys.add(company.company_key)
        for ticker in company.tickers:
            previous = seen_tickers.get(ticker)
            if previous and previous != company.company_key:
                raise ValueError(
                    f"ticker {ticker}가 여러 issuer에 연결됐습니다: "
                    f"{previous}, {company.company_key}"
                )
            seen_tickers[ticker] = company.company_key
        companies.append(company)

    if not companies:
        raise ValueError("universe에 회사가 없습니다.")

    return CompanyUniverse(
        universe_name=str(raw["universe_name"]),
        universe_mode=str(raw["universe_mode"]),
        as_of=str(raw["as_of"]),
        source_url=str(raw["source_url"]),
        companies=tuple(companies),
    )


def _resolve_project_path(path: str | Path) -> Path:
    resolved = Path(path)
    return resolved if resolved.is_absolute() else PROJECT_ROOT / resolved
