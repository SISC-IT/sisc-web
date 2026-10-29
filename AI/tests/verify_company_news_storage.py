from __future__ import annotations

# ruff: noqa: E402

import os
import sys
import unittest
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

import psycopg2
from psycopg2 import sql

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.append(str(PROJECT_ROOT))

from AI.modules.data_collector.components.news.contracts import (
    CollectedArticle,
    CollectionResult,
    CollectionStatus,
)
from AI.modules.data_collector.components.news.event_linker import (
    EventArticleCandidate,
    EventNewsRepository,
    EventRelationship,
    SecEvent,
    score_event_relevance,
)
from AI.modules.data_collector.components.news.repository import (
    CollectionAlreadyRunningError,
    NewsRepository,
)

UTC = timezone.utc
ACCEPTED_AT = datetime(2026, 7, 22, 20, 12, tzinfo=UTC)


def sec_event(**overrides) -> SecEvent:
    values = {
        "event_id": "AAPL_2026_000123",
        "accession_number": "0001234567-26-000123",
        "event_type": "8-K_2.02",
        "accepted_at": ACCEPTED_AT,
        "source_updated_at": ACCEPTED_AT + timedelta(minutes=1),
        "cik": "0000320193",
        "ticker": "AAPL",
        "event_text": "Apple reported quarterly revenue and earnings results.",
    }
    values.update(overrides)
    return SecEvent(**values)


def candidate(**overrides) -> EventArticleCandidate:
    values = {
        "article_id": 1,
        "title": "Apple reports record quarterly earnings",
        "published_at_utc": ACCEPTED_AT + timedelta(hours=1),
        "company_relevance_score": 0.9,
        "snippet": "Revenue and guidance beat estimates.",
    }
    values.update(overrides)
    return EventArticleCandidate(**values)


def collected_article(**overrides) -> CollectedArticle:
    values = {
        "company_key": "apple",
        "cik": "0000320193",
        "tickers": ("AAPL",),
        "provider": "google_news_rss",
        "provider_article_id": "provider-1",
        "title": "Apple reports record quarterly earnings",
        "provider_url": "https://example.com/news?id=42&utm_source=rss",
        "normalized_url": "https://example.com/news?id=42",
        "published_at_raw": "Wed, 22 Jul 2026 21:12:00 GMT",
        "published_at_utc": ACCEPTED_AT + timedelta(hours=1),
        "retrieved_at_utc": ACCEPTED_AT + timedelta(hours=2),
        "company_relevance_score": 0.9,
        "company_relevance_reasons": ("legal_name:title",),
        "company_relevance_version": "company-relevance-v1",
        "exact_identity_key": "provider-id:test",
        "exact_identity_method": "provider_article_id",
        "syndication_candidate_group_id": "syndication-candidate-v1:test",
        "fallback_text": "Revenue and guidance beat estimates.",
        "fallback_text_source": "provider_snippet",
        "snippet": "Revenue and guidance beat estimates.",
        "matched_ticker": "AAPL",
    }
    values.update(overrides)
    return CollectedArticle(**values)


def collection_result(article: CollectedArticle) -> CollectionResult:
    return CollectionResult(
        status=CollectionStatus.SUCCESS,
        articles=(article,),
        query_count=1,
        successful_query_count=1,
        started_at_utc=ACCEPTED_AT + timedelta(hours=2),
        completed_at_utc=ACCEPTED_AT + timedelta(hours=2, minutes=1),
        metadata={
            "provider": "google_news_rss",
            "requested_window": {
                "start_at_utc": (ACCEPTED_AT - timedelta(hours=1)).isoformat(),
                "end_at_utc": (ACCEPTED_AT + timedelta(hours=2)).isoformat(),
            },
            "universe": {
                "name": "S&P 100",
                "as_of": "2026-07-27",
            },
            "metrics": {"request_failure_rate": 0.0},
        },
    )


class EventRelevanceTest(unittest.TestCase):
    def test_direct_context_and_unrelated_are_separate(self) -> None:
        direct = score_event_relevance(sec_event(), candidate())
        context = score_event_relevance(
            sec_event(event_text=None),
            candidate(
                title="Apple expands services business",
                snippet=None,
                published_at_utc=ACCEPTED_AT + timedelta(hours=10),
            ),
        )
        unrelated = score_event_relevance(
            sec_event(event_text=None),
            candidate(
                title="The best Apple Watch bands for summer",
                snippet=None,
                published_at_utc=ACCEPTED_AT + timedelta(hours=40),
                company_relevance_score=0.6,
            ),
        )

        self.assertEqual(direct.relationship, EventRelationship.DIRECT)
        self.assertEqual(context.relationship, EventRelationship.CONTEXT)
        self.assertEqual(unrelated.relationship, EventRelationship.UNRELATED)


@unittest.skipUnless(
    os.environ.get("NEWS_TEST_DATABASE_URL"),
    "NEWS_TEST_DATABASE_URL is required for PostgreSQL integration tests",
)
class PostgreSQLNewsRepositoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.dsn = os.environ["NEWS_TEST_DATABASE_URL"]
        cls.schema = f"news_test_{uuid.uuid4().hex}"
        migration_path = (
            PROJECT_ROOT
            / "backend/src/main/resources/db/migration/V5__company_news_storage.sql"
        )
        connection = psycopg2.connect(cls.dsn)
        try:
            connection.autocommit = True
            with connection.cursor() as cursor:
                cursor.execute(
                    sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(cls.schema))
                )
                cursor.execute(
                    sql.SQL("SET search_path TO {}").format(sql.Identifier(cls.schema))
                )
                cursor.execute(migration_path.read_text(encoding="utf-8"))
        finally:
            connection.close()

    @classmethod
    def tearDownClass(cls) -> None:
        connection = psycopg2.connect(cls.dsn)
        try:
            connection.autocommit = True
            with connection.cursor() as cursor:
                cursor.execute(
                    sql.SQL("DROP SCHEMA {} CASCADE").format(sql.Identifier(cls.schema))
                )
        finally:
            connection.close()

    def connection_factory(self, _db_name: str):
        connection = psycopg2.connect(self.dsn)
        with connection.cursor() as cursor:
            cursor.execute(
                sql.SQL("SET search_path TO {}").format(sql.Identifier(self.schema))
            )
        return connection

    def scalar(self, query: str):
        connection = self.connection_factory("db")
        try:
            with connection.cursor() as cursor:
                cursor.execute(query)
                return cursor.fetchone()[0]
        finally:
            connection.close()

    def test_collection_upsert_is_idempotent_across_identity_aliases(self) -> None:
        repository = NewsRepository(connection_factory=self.connection_factory)
        repository.save_collection(collection_result(collected_article()))
        repository.save_collection(
            collection_result(
                collected_article(
                    provider_article_id="provider-2",
                    exact_identity_key="provider-id:test-2",
                    retrieved_at_utc=ACCEPTED_AT + timedelta(hours=3),
                )
            )
        )
        repository.save_collection(
            collection_result(
                collected_article(
                    provider_article_id="provider-1",
                    provider_url="https://example.com/news?id=99",
                    normalized_url="https://example.com/news?id=99",
                    exact_identity_key="provider-id:test",
                    retrieved_at_utc=ACCEPTED_AT + timedelta(hours=4),
                )
            )
        )

        self.assertEqual(self.scalar("SELECT count(*) FROM news_articles"), 1)
        self.assertEqual(
            self.scalar("SELECT count(*) FROM news_article_identities"),
            6,
        )
        self.assertEqual(
            self.scalar("SELECT count(*) FROM news_article_companies"),
            1,
        )
        self.assertEqual(
            self.scalar("SELECT count(*) FROM news_collection_runs"),
            3,
        )

    def test_event_reconciliation_is_idempotent(self) -> None:
        news_repository = NewsRepository(connection_factory=self.connection_factory)
        news_repository.save_collection(collection_result(collected_article()))
        event_repository = EventNewsRepository(
            connection_factory=self.connection_factory
        )
        first = event_repository.reconcile_event(sec_event())
        second = event_repository.reconcile_event(
            sec_event(
                source_updated_at=ACCEPTED_AT + timedelta(minutes=2),
                event_version=2,
            )
        )

        self.assertEqual(first[0].relationship, EventRelationship.DIRECT)
        self.assertEqual(second[0].relationship, EventRelationship.DIRECT)
        self.assertEqual(self.scalar("SELECT count(*) FROM event_news_links"), 1)
        self.assertEqual(
            self.scalar("SELECT source_event_version FROM event_news_links"),
            2,
        )

    def test_collection_advisory_lock_rejects_overlap(self) -> None:
        first = NewsRepository(connection_factory=self.connection_factory)
        second = NewsRepository(connection_factory=self.connection_factory)
        with first.collection_lock():
            with self.assertRaises(CollectionAlreadyRunningError):
                with second.collection_lock():
                    self.fail("second lock must not be acquired")


if __name__ == "__main__":
    unittest.main()
