"""Dedicated PostgreSQL fixtures; never use the project's configured production DB."""

import os
from pathlib import Path

import pytest

from AI.orchestration.series_store import SeriesStore


@pytest.fixture
def store():
    dsn = os.environ.get("INGESTION_TEST_DSN")
    if not dsn:
        pytest.skip("Dedicated PostgreSQL test database not configured")
    import psycopg2

    result = SeriesStore(connection_factory=lambda: psycopg2.connect(dsn))
    with result.transaction() as cur:
        cur.execute("SELECT current_database()")
        if not cur.fetchone()[0].startswith("ingestion_test"):
            pytest.fail("Test database name must start with ingestion_test")
        cur.execute("DROP SCHEMA IF EXISTS ingestion CASCADE")
        for path in sorted(
            (Path(__file__).parents[2] / "migrations").glob("[0-9][0-9][0-9]_*.sql")
        ):
            cur.execute(path.read_text(encoding="utf-8"))
        cur.execute("""CREATE TABLE IF NOT EXISTS public.price_data (
            ticker text, date date, open numeric, high numeric, low numeric, close numeric,
            volume bigint, adjusted_close numeric, amount numeric, PRIMARY KEY(date,ticker))""")
        cur.execute("""CREATE TABLE IF NOT EXISTS public.crypto_price_data (
            ticker text, date timestamp, open numeric, high numeric, low numeric, close numeric,
            volume numeric, PRIMARY KEY(date,ticker))""")
        cur.execute("TRUNCATE public.price_data, public.crypto_price_data")
    yield result
