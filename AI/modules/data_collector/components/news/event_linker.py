"""Deterministic SEC event-to-news linking and PostgreSQL reconciliation."""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from enum import Enum
from typing import Callable

from psycopg2.extras import Json, RealDictCursor

from AI.libs.database.connection import get_db_conn

from .windows import event_news_window, require_aware_utc

EVENT_RELEVANCE_VERSION = "event-relevance-v1"
DIRECT_THRESHOLD = 0.65
CONTEXT_THRESHOLD = 0.30

_STOP_WORDS = frozenset(
    {
        "about",
        "after",
        "against",
        "before",
        "company",
        "from",
        "have",
        "into",
        "more",
        "that",
        "their",
        "this",
        "with",
    }
)
_EVENT_KEYWORDS = {
    "2.02": ("earnings", "results", "revenue", "profit", "guidance", "quarter"),
    "earnings": ("earnings", "results", "revenue", "profit", "guidance", "quarter"),
    "acquisition": ("acquire", "acquisition", "merger", "deal", "transaction"),
    "regulation": ("regulator", "regulation", "antitrust", "lawsuit", "settlement"),
}
_GENERIC_DIRECT_TERMS = (
    "8-k",
    "filing",
    "announces",
    "announced",
    "reports",
    "reported",
)


class EventRelationship(str, Enum):
    DIRECT = "DIRECT"
    CONTEXT = "CONTEXT"
    UNRELATED = "UNRELATED"


@dataclass(frozen=True)
class SecEvent:
    event_id: str
    accession_number: str
    event_type: str
    accepted_at: datetime
    source_updated_at: datetime
    cik: str | None = None
    ticker: str | None = None
    company_key: str | None = None
    status: str = "active"
    event_version: int = 1
    event_text: str | None = None
    exhibit_text: str | None = None
    metadata: dict | None = None

    def __post_init__(self) -> None:
        for field_name in ("event_id", "accession_number", "event_type"):
            value = str(getattr(self, field_name)).strip()
            if not value:
                raise ValueError(f"{field_name} must not be empty")
            object.__setattr__(self, field_name, value)
        if self.status not in {"active", "inactive", "deleted"}:
            raise ValueError("status must be active, inactive, or deleted")
        if self.event_version < 1:
            raise ValueError("event_version must be positive")
        object.__setattr__(
            self,
            "accepted_at",
            require_aware_utc(self.accepted_at, "accepted_at"),
        )
        object.__setattr__(
            self,
            "source_updated_at",
            require_aware_utc(self.source_updated_at, "source_updated_at"),
        )
        cik = self.cik.strip() if self.cik else None
        if cik:
            if not cik.isdigit() or len(cik) > 10:
                raise ValueError("cik must contain at most 10 decimal digits")
            cik = cik.zfill(10)
        ticker = self.ticker.strip().upper() if self.ticker else None
        if cik is None and ticker is None:
            raise ValueError("an SEC event requires cik or ticker")
        object.__setattr__(self, "cik", cik)
        object.__setattr__(self, "ticker", ticker)
        object.__setattr__(self, "metadata", dict(self.metadata or {}))


@dataclass(frozen=True)
class EventArticleCandidate:
    article_id: int
    title: str
    published_at_utc: datetime
    company_relevance_score: float
    snippet: str | None = None
    fallback_text: str | None = None

    def __post_init__(self) -> None:
        object.__setattr__(
            self,
            "published_at_utc",
            require_aware_utc(self.published_at_utc, "published_at_utc"),
        )


@dataclass(frozen=True)
class EventRelevanceResult:
    relationship: EventRelationship
    score: float
    reasons: tuple[str, ...]
    version: str = EVENT_RELEVANCE_VERSION


def _normalized_text(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    return " ".join(re.sub(r"[^\w-]+", " ", normalized).split())


def _tokens(value: str) -> set[str]:
    return {
        token
        for token in _normalized_text(value).split()
        if len(token) >= 4 and token not in _STOP_WORDS
    }


def _event_terms(event_type: str) -> tuple[str, ...]:
    normalized = _normalized_text(event_type)
    terms: list[str] = []
    for event_marker, marker_terms in _EVENT_KEYWORDS.items():
        if event_marker in normalized:
            terms.extend(marker_terms)
    terms.extend(_GENERIC_DIRECT_TERMS)
    return tuple(dict.fromkeys(terms))


def score_event_relevance(
    event: SecEvent,
    article: EventArticleCandidate,
) -> EventRelevanceResult:
    """Score event relevance separately from company relevance."""

    article_text = " ".join(
        value
        for value in (article.title, article.snippet, article.fallback_text)
        if value
    )
    normalized_article = _normalized_text(article_text)
    reasons = ["same_company"]
    score = min(0.20, max(0.0, article.company_relevance_score) * 0.20)

    offset = abs((article.published_at_utc - event.accepted_at).total_seconds())
    if offset <= 6 * 3600:
        score += 0.25
        reasons.append("within_6_hours")
    elif offset <= 24 * 3600:
        score += 0.15
        reasons.append("within_24_hours")
    else:
        score += 0.05
        reasons.append("within_event_window")

    matched_terms = [
        term for term in _event_terms(event.event_type) if term in normalized_article
    ]
    if matched_terms:
        score += 0.35
        reasons.append(f"event_terms:{','.join(matched_terms[:4])}")

    event_document_text = " ".join(
        value for value in (event.event_text, event.exhibit_text) if value
    )
    if event_document_text:
        article_tokens = _tokens(article_text)
        overlap = article_tokens.intersection(_tokens(event_document_text))
        if overlap:
            overlap_score = min(0.25, 0.05 * len(overlap))
            score += overlap_score
            reasons.append(f"document_overlap:{','.join(sorted(overlap)[:5])}")

    score = round(min(1.0, score), 6)
    if score >= DIRECT_THRESHOLD:
        relationship = EventRelationship.DIRECT
    elif score >= CONTEXT_THRESHOLD:
        relationship = EventRelationship.CONTEXT
    else:
        relationship = EventRelationship.UNRELATED
    return EventRelevanceResult(relationship, score, tuple(reasons))


class EventNewsRepository:
    """Store SEC events and idempotently reconcile their news links."""

    def __init__(
        self,
        db_name: str = "db",
        *,
        connection_factory: Callable = get_db_conn,
    ) -> None:
        self.db_name = db_name
        self.connection_factory = connection_factory

    def _connect(self):
        return self.connection_factory(self.db_name)

    def upsert_event(self, event: SecEvent) -> None:
        connection = self._connect()
        try:
            with connection.cursor() as cursor:
                self._upsert_event(cursor, event)
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    @staticmethod
    def _upsert_event(cursor, event: SecEvent) -> None:
        cursor.execute(
            """
            INSERT INTO sec_events (
                event_id, company_key, ticker, cik, accession_number,
                event_type, accepted_at, source_updated_at, status,
                event_version, event_text, exhibit_text, metadata
            ) VALUES (
                %s, %s, %s, %s, %s,
                %s, %s, %s, %s,
                %s, %s, %s, %s
            )
            ON CONFLICT (event_id) DO UPDATE SET
                company_key = EXCLUDED.company_key,
                ticker = EXCLUDED.ticker,
                cik = EXCLUDED.cik,
                accession_number = EXCLUDED.accession_number,
                event_type = EXCLUDED.event_type,
                accepted_at = EXCLUDED.accepted_at,
                source_updated_at = EXCLUDED.source_updated_at,
                status = EXCLUDED.status,
                event_version = EXCLUDED.event_version,
                event_text = EXCLUDED.event_text,
                exhibit_text = EXCLUDED.exhibit_text,
                metadata = EXCLUDED.metadata,
                updated_at = now()
            """,
            (
                event.event_id,
                event.company_key,
                event.ticker,
                event.cik,
                event.accession_number,
                event.event_type,
                event.accepted_at,
                event.source_updated_at,
                event.status,
                event.event_version,
                event.event_text,
                event.exhibit_text,
                Json(event.metadata),
            ),
        )

    def get_event(self, event_id: str) -> SecEvent | None:
        connection = self._connect()
        try:
            with connection.cursor(cursor_factory=RealDictCursor) as cursor:
                cursor.execute(
                    """
                    SELECT event_id, company_key, ticker, cik, accession_number,
                           event_type, accepted_at, source_updated_at, status,
                           event_version, event_text, exhibit_text, metadata
                    FROM sec_events
                    WHERE event_id = %s
                    """,
                    (event_id,),
                )
                row = cursor.fetchone()
            return self._event_from_row(row) if row else None
        finally:
            connection.close()

    def sync_from_sec_filings(
        self,
        *,
        limit: int = 100,
        updated_after: datetime | None = None,
    ) -> list[SecEvent]:
        """Import collected SEC filings into the canonical event contract.

        This adapter expects the SEC collector's ``sec_filings`` and
        ``sec_filing_documents`` tables. It intentionally lives outside the
        SEC collector so either pipeline can be deployed first.
        """

        if not 1 <= limit <= 1000:
            raise ValueError("limit must be between 1 and 1000")
        connection = self._connect()
        try:
            with connection.cursor(cursor_factory=RealDictCursor) as cursor:
                cursor.execute(
                    """
                    SELECT
                        f.accession_number,
                        f.cik,
                        f.ticker,
                        f.event_type,
                        f.accepted_at,
                        f.updated_at AS source_updated_at,
                        f.metadata_json,
                        primary_doc.content_text AS event_text,
                        exhibits.exhibit_text
                    FROM sec_filings f
                    LEFT JOIN sec_events imported_event
                      ON imported_event.accession_number = f.accession_number
                     AND imported_event.event_type = f.event_type
                    LEFT JOIN LATERAL (
                        SELECT d.content_text
                        FROM sec_filing_documents d
                        WHERE d.accession_number = f.accession_number
                          AND d.is_primary = true
                        ORDER BY d.sequence_number, d.document_id
                        LIMIT 1
                    ) primary_doc ON true
                    LEFT JOIN LATERAL (
                        SELECT string_agg(d.content_text, E'\n\n' ORDER BY d.sequence_number)
                               AS exhibit_text
                        FROM sec_filing_documents d
                        WHERE d.accession_number = f.accession_number
                          AND d.is_exhibit = true
                          AND d.document_type IN ('EX-99.1', 'EX-99')
                    ) exhibits ON true
                    WHERE f.accepted_at IS NOT NULL
                      AND (%s IS NULL OR f.updated_at > %s)
                      AND (
                          imported_event.event_id IS NULL
                          OR f.updated_at > imported_event.source_updated_at
                      )
                    ORDER BY f.updated_at, f.accession_number
                    LIMIT %s
                    """,
                    (updated_after, updated_after, limit),
                )
                rows = cursor.fetchall()
                events = [self._event_from_sec_filing_row(row) for row in rows]
                for event in events:
                    self._upsert_event(cursor, event)
            connection.commit()
            return events
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    @staticmethod
    def _event_from_sec_filing_row(row) -> SecEvent:
        ticker_or_cik = (row.get("ticker") or row["cik"]).upper()
        compact_accession = row["accession_number"].replace("-", "")
        event_slug = re.sub(r"[^A-Za-z0-9.]+", "_", row["event_type"]).strip("_")
        source_updated_at = row["source_updated_at"]
        event_version = max(1, int(source_updated_at.timestamp() * 1_000_000))
        return SecEvent(
            event_id=f"{ticker_or_cik}_{compact_accession}_{event_slug}",
            accession_number=row["accession_number"],
            event_type=row["event_type"],
            accepted_at=row["accepted_at"],
            source_updated_at=source_updated_at,
            cik=row.get("cik"),
            ticker=row.get("ticker"),
            event_version=event_version,
            event_text=row.get("event_text"),
            exhibit_text=row.get("exhibit_text"),
            metadata=dict(row.get("metadata_json") or {}),
        )

    def list_due_events(
        self,
        *,
        now: datetime | None = None,
        limit: int = 100,
    ) -> list[SecEvent]:
        if not 1 <= limit <= 1000:
            raise ValueError("limit must be between 1 and 1000")
        current = require_aware_utc(now or datetime.now(timezone.utc), "now")
        connection = self._connect()
        try:
            with connection.cursor(cursor_factory=RealDictCursor) as cursor:
                cursor.execute(
                    """
                    SELECT event_id, company_key, ticker, cik, accession_number,
                           event_type, accepted_at, source_updated_at, status,
                           event_version, event_text, exhibit_text, metadata
                    FROM sec_events
                    WHERE status = 'active'
                      AND accepted_at <= %s
                      AND (
                          last_reconciled_at IS NULL
                          OR source_updated_at > last_reconciled_at
                          OR (
                              accepted_at + interval '48 hours' > %s
                              AND COALESCE(next_reconcile_at, accepted_at) <= %s
                          )
                      )
                    ORDER BY COALESCE(next_reconcile_at, accepted_at), event_id
                    LIMIT %s
                    """,
                    (current, current, current, limit),
                )
                rows = cursor.fetchall()
            return [self._event_from_row(row) for row in rows]
        finally:
            connection.close()

    @staticmethod
    def _event_from_row(row) -> SecEvent:
        return SecEvent(
            event_id=row["event_id"],
            company_key=row.get("company_key"),
            ticker=row.get("ticker"),
            cik=row.get("cik"),
            accession_number=row["accession_number"],
            event_type=row["event_type"],
            accepted_at=row["accepted_at"],
            source_updated_at=row["source_updated_at"],
            status=row["status"],
            event_version=int(row["event_version"]),
            event_text=row.get("event_text"),
            exhibit_text=row.get("exhibit_text"),
            metadata=dict(row.get("metadata") or {}),
        )

    def reconcile_event(
        self,
        event: SecEvent,
        *,
        now: datetime | None = None,
    ) -> list[EventRelevanceResult]:
        current = require_aware_utc(now or datetime.now(timezone.utc), "now")
        window_start, window_end = event_news_window(event.accepted_at)
        connection = self._connect()
        try:
            with connection.cursor(cursor_factory=RealDictCursor) as cursor:
                cursor.execute(
                    "SELECT pg_advisory_xact_lock(hashtextextended(%s, 0))",
                    (f"event-news:{event.event_id}",),
                )
                self._upsert_event(cursor, event)
                if event.status != "active":
                    cursor.execute(
                        "DELETE FROM event_news_links WHERE event_id = %s",
                        (event.event_id,),
                    )
                    self._mark_reconciled(cursor, event, current, None)
                    connection.commit()
                    return []

                candidates = self._load_candidates(
                    cursor,
                    event,
                    window_start,
                    window_end,
                )
                results: list[EventRelevanceResult] = []
                candidate_ids: list[int] = []
                for candidate in candidates:
                    relevance = score_event_relevance(event, candidate)
                    results.append(relevance)
                    candidate_ids.append(candidate.article_id)
                    cursor.execute(
                        """
                        INSERT INTO event_news_links (
                            event_id, article_id, relationship,
                            event_relevance_score, event_relevance_reasons,
                            event_relevance_version, published_offset_seconds,
                            source_event_version, source_event_updated_at
                        ) VALUES (
                            %s, %s, %s,
                            %s, %s,
                            %s, %s,
                            %s, %s
                        )
                        ON CONFLICT (event_id, article_id) DO UPDATE SET
                            relationship = EXCLUDED.relationship,
                            event_relevance_score = EXCLUDED.event_relevance_score,
                            event_relevance_reasons = EXCLUDED.event_relevance_reasons,
                            event_relevance_version = EXCLUDED.event_relevance_version,
                            published_offset_seconds = EXCLUDED.published_offset_seconds,
                            source_event_version = EXCLUDED.source_event_version,
                            source_event_updated_at = EXCLUDED.source_event_updated_at,
                            updated_at = now()
                        """,
                        (
                            event.event_id,
                            candidate.article_id,
                            relevance.relationship.value,
                            relevance.score,
                            list(relevance.reasons),
                            relevance.version,
                            int(
                                (
                                    candidate.published_at_utc - event.accepted_at
                                ).total_seconds()
                            ),
                            event.event_version,
                            event.source_updated_at,
                        ),
                    )

                if candidate_ids:
                    cursor.execute(
                        """
                        DELETE FROM event_news_links
                        WHERE event_id = %s
                          AND NOT (article_id = ANY(%s))
                        """,
                        (event.event_id, candidate_ids),
                    )
                else:
                    cursor.execute(
                        "DELETE FROM event_news_links WHERE event_id = %s",
                        (event.event_id,),
                    )

                next_reconcile = (
                    min(current + timedelta(hours=1), window_end)
                    if current < window_end
                    else None
                )
                self._mark_reconciled(cursor, event, current, next_reconcile)
            connection.commit()
            return results
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    @staticmethod
    def _load_candidates(
        cursor,
        event: SecEvent,
        window_start: datetime,
        window_end: datetime,
    ) -> list[EventArticleCandidate]:
        if event.cik:
            identity_clause = "nac.cik = %s"
            identity_value = event.cik
        else:
            identity_clause = "%s = ANY(nac.tickers)"
            identity_value = event.ticker
        cursor.execute(
            f"""
            SELECT a.article_id, a.title, a.published_at_utc,
                   a.snippet, a.fallback_text,
                   nac.company_relevance_score
            FROM news_articles a
            JOIN news_article_companies nac ON nac.article_id = a.article_id
            WHERE {identity_clause}
              AND a.published_at_utc BETWEEN %s AND %s
            ORDER BY a.published_at_utc, a.article_id
            """,
            (identity_value, window_start, window_end),
        )
        return [
            EventArticleCandidate(
                article_id=int(row["article_id"]),
                title=row["title"],
                published_at_utc=row["published_at_utc"],
                company_relevance_score=float(row["company_relevance_score"]),
                snippet=row.get("snippet"),
                fallback_text=row.get("fallback_text"),
            )
            for row in cursor.fetchall()
        ]

    @staticmethod
    def _mark_reconciled(
        cursor,
        event: SecEvent,
        reconciled_at: datetime,
        next_reconcile_at: datetime | None,
    ) -> None:
        cursor.execute(
            """
            UPDATE sec_events
            SET last_reconciled_at = %s,
                next_reconcile_at = %s,
                updated_at = now()
            WHERE event_id = %s
            """,
            (reconciled_at, next_reconcile_at, event.event_id),
        )

    def reconcile_due_events(
        self,
        *,
        now: datetime | None = None,
        limit: int = 100,
    ) -> dict[str, int]:
        current = require_aware_utc(now or datetime.now(timezone.utc), "now")
        events = self.list_due_events(now=current, limit=limit)
        linked = 0
        for event in events:
            linked += len(self.reconcile_event(event, now=current))
        return {"event_count": len(events), "link_count": linked}
