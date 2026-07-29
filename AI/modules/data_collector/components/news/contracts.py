"""Stable data contracts for the company-news collection pipeline.

The collector is intentionally independent from the SEC event pipeline.  These
types therefore describe company-level collection and relevance only; event
identifiers and event-level relevance belong to the later linking stage.
"""

from __future__ import annotations

from dataclasses import dataclass, field, fields, is_dataclass
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Dict, Mapping, Optional, Tuple


class CollectionStatus(str, Enum):
    """Outcome of one collection operation.

    ``NO_RESULTS`` and ``NO_RELEVANT_NEWS`` are successful, observable
    outcomes.  They must not be collapsed into ``PROVIDER_ERROR``.
    """

    SUCCESS = "success"
    NO_RESULTS = "no_results"
    NO_RELEVANT_NEWS = "no_relevant_news"
    PARTIAL_SUCCESS = "partial_success"
    PROVIDER_ERROR = "provider_error"
    INVALID_CONFIG = "invalid_config"


def _utc_datetime(value: datetime, field_name: str) -> datetime:
    """Return an aware UTC datetime, rejecting ambiguous naive values."""

    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field_name} must be timezone-aware")
    return value.astimezone(timezone.utc)


def _clean_required(value: str, field_name: str) -> str:
    cleaned = value.strip()
    if not cleaned:
        raise ValueError(f"{field_name} must not be empty")
    return cleaned


def _clean_optional(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None
    cleaned = value.strip()
    return cleaned or None


def _datetime_to_iso8601(value: datetime) -> str:
    normalized = _utc_datetime(value, "datetime")
    return normalized.isoformat().replace("+00:00", "Z")


def to_serializable(value: Any) -> Any:
    """Recursively convert contracts to JSON-serializable primitives."""

    if isinstance(value, datetime):
        return _datetime_to_iso8601(value)
    if isinstance(value, Enum):
        return value.value
    if is_dataclass(value) and not isinstance(value, type):
        return {
            item.name: to_serializable(getattr(value, item.name))
            for item in fields(value)
        }
    if isinstance(value, Mapping):
        return {
            str(key): to_serializable(item_value) for key, item_value in value.items()
        }
    if isinstance(value, (tuple, list)):
        return [to_serializable(item) for item in value]
    if isinstance(value, (set, frozenset)):
        return [to_serializable(item) for item in sorted(value, key=str)]
    return value


class SerializableContract:
    """Mixin shared by immutable collection contracts."""

    def to_dict(self) -> Dict[str, Any]:
        serialized = to_serializable(self)
        if not isinstance(serialized, dict):
            raise TypeError("contract did not serialize to a dictionary")
        return serialized


@dataclass(frozen=True)
class CompanyTarget(SerializableContract):
    """One issuer to search, potentially represented by multiple share classes."""

    company_key: str
    cik: Optional[str]
    legal_name: str
    tickers: Tuple[str, ...]
    aliases: Tuple[str, ...] = ()
    enabled: bool = True
    ambiguous_tickers: Tuple[str, ...] = ()

    def __post_init__(self) -> None:
        if not isinstance(self.enabled, bool):
            raise ValueError("enabled must be a boolean")
        company_key = _clean_required(self.company_key, "company_key")
        legal_name = _clean_required(self.legal_name, "legal_name")
        tickers = tuple(
            dict.fromkeys(
                _clean_required(ticker, "ticker").upper() for ticker in self.tickers
            )
        )
        aliases = tuple(
            dict.fromkeys(_clean_required(alias, "alias") for alias in self.aliases)
        )
        ambiguous_tickers = tuple(
            dict.fromkeys(
                _clean_required(ticker, "ambiguous_ticker").upper()
                for ticker in self.ambiguous_tickers
            )
        )

        if not tickers:
            raise ValueError("tickers must contain at least one ticker")
        unknown_ambiguous = set(ambiguous_tickers).difference(tickers)
        if unknown_ambiguous:
            names = ", ".join(sorted(unknown_ambiguous))
            raise ValueError(f"ambiguous_tickers must be present in tickers: {names}")

        cik = _clean_optional(self.cik)
        if cik is not None:
            if not cik.isdigit() or len(cik) > 10:
                raise ValueError("cik must contain at most 10 decimal digits")
            cik = cik.zfill(10)

        object.__setattr__(self, "company_key", company_key)
        object.__setattr__(self, "cik", cik)
        object.__setattr__(self, "legal_name", legal_name)
        object.__setattr__(self, "tickers", tickers)
        object.__setattr__(self, "aliases", aliases)
        object.__setattr__(self, "ambiguous_tickers", ambiguous_tickers)


@dataclass(frozen=True)
class ProviderArticle(SerializableContract):
    """Provider response after parsing but before relevance and deduplication."""

    provider: str
    provider_article_id: Optional[str]
    title: str
    provider_url: str
    published_at_raw: str
    published_at_utc: datetime
    source: Optional[str] = None
    snippet: Optional[str] = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "provider", _clean_required(self.provider, "provider"))
        object.__setattr__(self, "title", _clean_required(self.title, "title"))
        object.__setattr__(
            self,
            "provider_url",
            _clean_required(self.provider_url, "provider_url"),
        )
        object.__setattr__(
            self,
            "published_at_raw",
            _clean_required(self.published_at_raw, "published_at_raw"),
        )
        object.__setattr__(
            self,
            "published_at_utc",
            _utc_datetime(self.published_at_utc, "published_at_utc"),
        )
        object.__setattr__(
            self,
            "provider_article_id",
            _clean_optional(self.provider_article_id),
        )
        object.__setattr__(self, "source", _clean_optional(self.source))
        object.__setattr__(self, "snippet", _clean_optional(self.snippet))


@dataclass(frozen=True)
class CollectedArticle(SerializableContract):
    """Company-level news article ready for persistence or JSON output."""

    company_key: str
    cik: Optional[str]
    tickers: Tuple[str, ...]
    provider: str
    provider_article_id: Optional[str]
    title: str
    provider_url: str
    normalized_url: Optional[str]
    published_at_raw: str
    published_at_utc: datetime
    retrieved_at_utc: datetime
    company_relevance_score: float
    company_relevance_reasons: Tuple[str, ...]
    company_relevance_version: str
    exact_identity_key: str
    exact_identity_method: str
    syndication_candidate_group_id: str
    syndication_candidate_group_method: str = "normalized_title_6h_bucket_v1"
    syndication_candidate_group_confidence: str = "low"
    resolved_url: Optional[str] = None
    url_resolution_status: str = "not_attempted"
    content_text: Optional[str] = None
    content_collection_status: str = "not_attempted"
    content_failure_reason: Optional[str] = None
    fallback_text: Optional[str] = None
    fallback_text_source: str = "title"
    source: Optional[str] = None
    snippet: Optional[str] = None
    matched_ticker: Optional[str] = None

    def __post_init__(self) -> None:
        object.__setattr__(
            self, "company_key", _clean_required(self.company_key, "company_key")
        )
        cik = _clean_optional(self.cik)
        if cik is not None:
            if not cik.isdigit() or len(cik) > 10:
                raise ValueError("cik must contain at most 10 decimal digits")
            cik = cik.zfill(10)
        object.__setattr__(self, "cik", cik)
        tickers = tuple(
            dict.fromkeys(
                _clean_required(ticker, "ticker").upper() for ticker in self.tickers
            )
        )
        if not tickers:
            raise ValueError("tickers must contain at least one ticker")
        object.__setattr__(self, "tickers", tickers)
        object.__setattr__(self, "provider", _clean_required(self.provider, "provider"))
        object.__setattr__(self, "title", _clean_required(self.title, "title"))
        object.__setattr__(
            self,
            "provider_url",
            _clean_required(self.provider_url, "provider_url"),
        )
        object.__setattr__(
            self,
            "published_at_raw",
            _clean_required(self.published_at_raw, "published_at_raw"),
        )
        object.__setattr__(
            self,
            "published_at_utc",
            _utc_datetime(self.published_at_utc, "published_at_utc"),
        )
        object.__setattr__(
            self,
            "retrieved_at_utc",
            _utc_datetime(self.retrieved_at_utc, "retrieved_at_utc"),
        )
        object.__setattr__(
            self,
            "provider_article_id",
            _clean_optional(self.provider_article_id),
        )
        object.__setattr__(self, "normalized_url", _clean_optional(self.normalized_url))
        object.__setattr__(self, "resolved_url", _clean_optional(self.resolved_url))
        object.__setattr__(
            self,
            "url_resolution_status",
            _clean_required(self.url_resolution_status, "url_resolution_status"),
        )
        object.__setattr__(self, "content_text", _clean_optional(self.content_text))
        object.__setattr__(
            self,
            "content_collection_status",
            _clean_required(
                self.content_collection_status,
                "content_collection_status",
            ),
        )
        object.__setattr__(
            self,
            "content_failure_reason",
            _clean_optional(self.content_failure_reason),
        )
        object.__setattr__(self, "fallback_text", _clean_optional(self.fallback_text))
        fallback_source = _clean_required(
            self.fallback_text_source,
            "fallback_text_source",
        )
        if fallback_source not in ("provider_snippet", "title"):
            raise ValueError("fallback_text_source must be provider_snippet or title")
        object.__setattr__(self, "fallback_text_source", fallback_source)
        object.__setattr__(self, "source", _clean_optional(self.source))
        object.__setattr__(self, "snippet", _clean_optional(self.snippet))
        matched_ticker = _clean_optional(self.matched_ticker)
        object.__setattr__(
            self,
            "matched_ticker",
            matched_ticker.upper() if matched_ticker is not None else None,
        )

        score = float(self.company_relevance_score)
        if not 0.0 <= score <= 1.0:
            raise ValueError("company_relevance_score must be between 0 and 1")
        object.__setattr__(self, "company_relevance_score", round(score, 6))
        object.__setattr__(
            self,
            "company_relevance_reasons",
            tuple(dict.fromkeys(self.company_relevance_reasons)),
        )
        object.__setattr__(
            self,
            "company_relevance_version",
            _clean_required(
                self.company_relevance_version,
                "company_relevance_version",
            ),
        )
        object.__setattr__(
            self,
            "exact_identity_key",
            _clean_required(self.exact_identity_key, "exact_identity_key"),
        )
        object.__setattr__(
            self,
            "exact_identity_method",
            _clean_required(
                self.exact_identity_method,
                "exact_identity_method",
            ),
        )
        object.__setattr__(
            self,
            "syndication_candidate_group_id",
            _clean_required(
                self.syndication_candidate_group_id,
                "syndication_candidate_group_id",
            ),
        )
        object.__setattr__(
            self,
            "syndication_candidate_group_method",
            _clean_required(
                self.syndication_candidate_group_method,
                "syndication_candidate_group_method",
            ),
        )
        confidence = _clean_required(
            self.syndication_candidate_group_confidence,
            "syndication_candidate_group_confidence",
        ).casefold()
        if confidence not in ("low", "medium", "high"):
            raise ValueError(
                "syndication_candidate_group_confidence must be low, medium, or high"
            )
        object.__setattr__(
            self,
            "syndication_candidate_group_confidence",
            confidence,
        )


@dataclass(frozen=True)
class CollectionResult(SerializableContract):
    """Aggregate result without conflating empty feeds and provider failures."""

    status: CollectionStatus
    articles: Tuple[CollectedArticle, ...] = ()
    failure_reason: Optional[str] = None
    errors: Tuple[str, ...] = ()
    query_count: int = 0
    successful_query_count: int = 0
    failed_query_count: int = 0
    started_at_utc: Optional[datetime] = None
    completed_at_utc: Optional[datetime] = None
    metadata: Mapping[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        status = self.status
        if not isinstance(status, CollectionStatus):
            status = CollectionStatus(status)
            object.__setattr__(self, "status", status)

        object.__setattr__(self, "articles", tuple(self.articles))
        object.__setattr__(self, "failure_reason", _clean_optional(self.failure_reason))
        object.__setattr__(
            self,
            "errors",
            tuple(
                error
                for error in (_clean_optional(item) for item in self.errors)
                if error is not None
            ),
        )

        for field_name in (
            "query_count",
            "successful_query_count",
            "failed_query_count",
        ):
            value = getattr(self, field_name)
            if value < 0:
                raise ValueError(f"{field_name} must not be negative")

        if self.successful_query_count + self.failed_query_count > self.query_count:
            raise ValueError("successful and failed query counts exceed query_count")

        if self.started_at_utc is not None:
            object.__setattr__(
                self,
                "started_at_utc",
                _utc_datetime(self.started_at_utc, "started_at_utc"),
            )
        if self.completed_at_utc is not None:
            object.__setattr__(
                self,
                "completed_at_utc",
                _utc_datetime(self.completed_at_utc, "completed_at_utc"),
            )
        if (
            self.started_at_utc is not None
            and self.completed_at_utc is not None
            and self.completed_at_utc < self.started_at_utc
        ):
            raise ValueError("completed_at_utc must not precede started_at_utc")

        if (
            status in (CollectionStatus.NO_RESULTS, CollectionStatus.NO_RELEVANT_NEWS)
            and self.articles
        ):
            raise ValueError(f"{status.value} cannot contain articles")

        object.__setattr__(self, "metadata", dict(self.metadata))

    @property
    def relevant_article_count(self) -> Optional[int]:
        """Return ``None`` when collection itself failed."""

        if self.status in (
            CollectionStatus.PROVIDER_ERROR,
            CollectionStatus.INVALID_CONFIG,
        ):
            return None
        return len(self.articles)

    def to_dict(self) -> Dict[str, Any]:
        payload = super().to_dict()
        internal_status = self.status
        payload.pop("status")
        if internal_status in (
            CollectionStatus.SUCCESS,
            CollectionStatus.NO_RESULTS,
            CollectionStatus.NO_RELEVANT_NEWS,
        ):
            collection_status = "success"
        elif internal_status == CollectionStatus.PARTIAL_SUCCESS:
            collection_status = "partial_success"
        else:
            collection_status = "failed"

        outcome_map = {
            CollectionStatus.SUCCESS: "results",
            CollectionStatus.NO_RESULTS: "no_results",
            CollectionStatus.NO_RELEVANT_NEWS: "no_relevant_news",
            CollectionStatus.PARTIAL_SUCCESS: "partial_results",
            CollectionStatus.PROVIDER_ERROR: "provider_error",
            CollectionStatus.INVALID_CONFIG: "invalid_config",
        }
        payload["collection_status"] = collection_status
        payload["collection_outcome"] = outcome_map[internal_status]
        payload["relevant_article_count"] = self.relevant_article_count
        return payload
