"""PostgreSQL persistence for independent company-news collection results."""

from __future__ import annotations

import hashlib
import uuid
from contextlib import contextmanager
from datetime import datetime, timezone
from typing import Callable, Iterator

from psycopg2.extras import Json

from AI.libs.database.connection import get_db_conn

from .contracts import CollectedArticle, CollectionResult

COLLECTION_LOCK_KEY = 0x434F4D504E455753


class CollectionAlreadyRunningError(RuntimeError):
    """Raised when another collector owns the PostgreSQL advisory lock."""


def _parse_utc(value: str | datetime, field_name: str) -> datetime:
    if isinstance(value, str):
        normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
        value = datetime.fromisoformat(normalized)
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field_name} must be timezone-aware")
    return value.astimezone(timezone.utc)


def _provider_url_hash(article: CollectedArticle) -> str:
    provider = article.provider.strip().casefold()
    payload = f"{provider}\0{article.provider_url.strip()}"
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


class NewsRepository:
    """Persist collection runs and articles with cross-run exact deduplication."""

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

    @contextmanager
    def collection_lock(self) -> Iterator[None]:
        """Hold a session advisory lock across collection and persistence."""

        connection = self._connect()
        previous_autocommit = connection.autocommit
        if not previous_autocommit:
            connection.rollback()
        connection.autocommit = True
        acquired = False
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    "SELECT pg_try_advisory_lock(%s)",
                    (COLLECTION_LOCK_KEY,),
                )
                row = cursor.fetchone()
                acquired = bool(row and row[0])
            if not acquired:
                raise CollectionAlreadyRunningError(
                    "another company-news collection is already running"
                )
            yield
        finally:
            if acquired:
                with connection.cursor() as cursor:
                    cursor.execute(
                        "SELECT pg_advisory_unlock(%s)",
                        (COLLECTION_LOCK_KEY,),
                    )
            connection.autocommit = previous_autocommit
            connection.close()

    def save_collection(
        self,
        result: CollectionResult,
        *,
        run_id: uuid.UUID | str | None = None,
    ) -> uuid.UUID:
        """Save one complete result transactionally and return its run UUID."""

        normalized_run_id = uuid.UUID(str(run_id)) if run_id else uuid.uuid4()
        payload = result.to_dict()
        requested_window = result.metadata.get("requested_window") or {}
        start_value = requested_window.get("start_at_utc")
        end_value = requested_window.get("end_at_utc")
        if start_value is None or end_value is None:
            raise ValueError("result metadata must include requested_window")
        requested_start = _parse_utc(start_value, "requested_start_at")
        requested_end = _parse_utc(end_value, "requested_end_at")
        universe = result.metadata.get("universe") or {}
        universe_as_of = universe.get("as_of")

        connection = self._connect()
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO news_collection_runs (
                        run_id, provider, universe_name, universe_as_of,
                        requested_start_at, requested_end_at,
                        started_at, completed_at, collection_status,
                        collection_outcome, failure_reason, query_count,
                        successful_query_count, failed_query_count,
                        relevant_article_count, errors, metrics, metadata
                    ) VALUES (
                        %s, %s, %s, %s,
                        %s, %s,
                        %s, %s, %s,
                        %s, %s, %s,
                        %s, %s,
                        %s, %s, %s, %s
                    )
                    """,
                    (
                        str(normalized_run_id),
                        str(result.metadata.get("provider", "unknown")),
                        universe.get("name"),
                        universe_as_of,
                        requested_start,
                        requested_end,
                        result.started_at_utc,
                        result.completed_at_utc,
                        payload["collection_status"],
                        payload["collection_outcome"],
                        result.failure_reason,
                        result.query_count,
                        result.successful_query_count,
                        result.failed_query_count,
                        result.relevant_article_count,
                        Json(list(result.errors)),
                        Json(dict(result.metadata.get("metrics") or {})),
                        Json(dict(result.metadata)),
                    ),
                )

                for article in result.articles:
                    article_id = self._upsert_article(cursor, article)
                    self._upsert_company_link(
                        cursor,
                        article_id,
                        article,
                        normalized_run_id,
                    )
            connection.commit()
            return normalized_run_id
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    @staticmethod
    def _identity_entries(
        article: CollectedArticle,
    ) -> tuple[tuple[str, str], ...]:
        provider = article.provider.strip().casefold()
        url_hash = _provider_url_hash(article)
        entries = {
            (f"provider-url:{url_hash}", "provider_url_hash"),
        }
        if article.provider_article_id:
            provider_id_hash = hashlib.sha256(
                f"{provider}\0{article.provider_article_id.strip()}".encode("utf-8")
            ).hexdigest()
            entries.add((f"provider-id:{provider_id_hash}", "provider_article_id"))
        if article.normalized_url:
            normalized_hash = hashlib.sha256(
                article.normalized_url.encode("utf-8")
            ).hexdigest()
            entries.add((f"normalized-url:{normalized_hash}", "normalized_url"))
        return tuple(sorted(entries))

    @classmethod
    def _upsert_article(cls, cursor, article: CollectedArticle) -> int:
        identity_entries = cls._identity_entries(article)
        identity_keys = [entry[0] for entry in identity_entries]
        for lock_key in identity_keys:
            cursor.execute(
                "SELECT pg_advisory_xact_lock(hashtextextended(%s, 0))",
                (lock_key,),
            )

        url_hash = _provider_url_hash(article)
        cursor.execute(
            """
            SELECT DISTINCT article_id
            FROM news_article_identities
            WHERE identity_key = ANY(%s)
            ORDER BY article_id
            """,
            (identity_keys,),
        )
        identity_rows = cursor.fetchall()
        if len(identity_rows) > 1:
            raise RuntimeError("exact identities resolve to multiple stored articles")
        row = identity_rows[0] if identity_rows else None
        if row is None:
            cursor.execute(
                """
                SELECT article_id
                FROM news_articles
                WHERE (
                    %s IS NOT NULL
                    AND provider = %s
                    AND provider_article_id = %s
                ) OR (
                    %s IS NOT NULL
                    AND normalized_url = %s
                ) OR (
                    provider = %s
                    AND provider_url_hash = %s
                )
                ORDER BY article_id
                LIMIT 1
                FOR UPDATE
                """,
                (
                    article.provider_article_id,
                    article.provider,
                    article.provider_article_id,
                    article.normalized_url,
                    article.normalized_url,
                    article.provider,
                    url_hash,
                ),
            )
            row = cursor.fetchone()
        values = cls._article_values(article, url_hash)
        if row:
            article_id = int(row[0])
            cursor.execute(
                "SELECT article_id FROM news_articles WHERE article_id = %s FOR UPDATE",
                (article_id,),
            )
            cursor.execute(
                """
                UPDATE news_articles SET
                    provider_article_id = COALESCE(%s, provider_article_id),
                    title = %s,
                    provider_url = %s,
                    normalized_url = COALESCE(%s, normalized_url),
                    provider_url_hash = %s,
                    published_at_raw = %s,
                    published_at_utc = %s,
                    first_retrieved_at_utc = LEAST(first_retrieved_at_utc, %s),
                    last_retrieved_at_utc = GREATEST(last_retrieved_at_utc, %s),
                    source = %s,
                    snippet = %s,
                    resolved_url = %s,
                    url_resolution_status = %s,
                    content_text = %s,
                    content_collection_status = %s,
                    content_failure_reason = %s,
                    fallback_text = %s,
                    fallback_text_source = %s,
                    exact_identity_key = %s,
                    exact_identity_method = %s,
                    syndication_candidate_group_id = %s,
                    syndication_candidate_group_method = %s,
                    syndication_candidate_group_confidence = %s,
                    updated_at = now()
                WHERE article_id = %s
                """,
                (*values[1:], article_id),
            )
            cls._register_identities(cursor, article_id, identity_entries)
            return article_id

        cursor.execute(
            """
            INSERT INTO news_articles (
                provider, provider_article_id, title, provider_url,
                normalized_url, provider_url_hash, published_at_raw,
                published_at_utc, first_retrieved_at_utc,
                last_retrieved_at_utc, source, snippet, resolved_url,
                url_resolution_status, content_text,
                content_collection_status, content_failure_reason,
                fallback_text, fallback_text_source, exact_identity_key,
                exact_identity_method, syndication_candidate_group_id,
                syndication_candidate_group_method,
                syndication_candidate_group_confidence
            ) VALUES (
                %s, %s, %s, %s,
                %s, %s, %s,
                %s, %s,
                %s, %s, %s, %s,
                %s, %s,
                %s, %s,
                %s, %s, %s,
                %s, %s,
                %s, %s
            )
            RETURNING article_id
            """,
            values,
        )
        article_id = int(cursor.fetchone()[0])
        cls._register_identities(cursor, article_id, identity_entries)
        return article_id

    @staticmethod
    def _register_identities(cursor, article_id: int, identity_entries) -> None:
        for identity_key, identity_method in identity_entries:
            cursor.execute(
                """
                INSERT INTO news_article_identities (
                    identity_key, identity_method, article_id
                ) VALUES (%s, %s, %s)
                ON CONFLICT (identity_key) DO NOTHING
                """,
                (identity_key, identity_method, article_id),
            )
        cursor.execute(
            """
            SELECT DISTINCT article_id
            FROM news_article_identities
            WHERE identity_key = ANY(%s)
            """,
            ([entry[0] for entry in identity_entries],),
        )
        resolved_ids = {int(row[0]) for row in cursor.fetchall()}
        if resolved_ids != {article_id}:
            raise RuntimeError("an exact identity is already owned by another article")

    @staticmethod
    def _article_values(
        article: CollectedArticle,
        url_hash: str,
    ) -> tuple:
        return (
            article.provider,
            article.provider_article_id,
            article.title,
            article.provider_url,
            article.normalized_url,
            url_hash,
            article.published_at_raw,
            article.published_at_utc,
            article.retrieved_at_utc,
            article.retrieved_at_utc,
            article.source,
            article.snippet,
            article.resolved_url,
            article.url_resolution_status,
            article.content_text,
            article.content_collection_status,
            article.content_failure_reason,
            article.fallback_text,
            article.fallback_text_source,
            article.exact_identity_key,
            article.exact_identity_method,
            article.syndication_candidate_group_id,
            article.syndication_candidate_group_method,
            article.syndication_candidate_group_confidence,
        )

    @staticmethod
    def _upsert_company_link(
        cursor,
        article_id: int,
        article: CollectedArticle,
        run_id: uuid.UUID,
    ) -> None:
        cursor.execute(
            """
            INSERT INTO news_article_companies (
                article_id, company_key, cik, tickers, matched_ticker,
                company_relevance_score, company_relevance_reasons,
                company_relevance_version, first_collection_run_id,
                last_collection_run_id, first_seen_at, last_seen_at
            ) VALUES (
                %s, %s, %s, %s, %s,
                %s, %s, %s, %s,
                %s, %s, %s
            )
            ON CONFLICT (article_id, company_key) DO UPDATE SET
                cik = EXCLUDED.cik,
                tickers = EXCLUDED.tickers,
                matched_ticker = EXCLUDED.matched_ticker,
                company_relevance_score = EXCLUDED.company_relevance_score,
                company_relevance_reasons = EXCLUDED.company_relevance_reasons,
                company_relevance_version = EXCLUDED.company_relevance_version,
                last_collection_run_id = EXCLUDED.last_collection_run_id,
                last_seen_at = GREATEST(
                    news_article_companies.last_seen_at,
                    EXCLUDED.last_seen_at
                )
            """,
            (
                article_id,
                article.company_key,
                article.cik,
                list(article.tickers),
                article.matched_ticker,
                article.company_relevance_score,
                list(article.company_relevance_reasons),
                article.company_relevance_version,
                str(run_id),
                str(run_id),
                article.retrieved_at_utc,
                article.retrieved_at_utc,
            ),
        )
