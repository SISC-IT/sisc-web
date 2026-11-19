"""Real PostgreSQL acceptance tests. Require a dedicated, disposable database.

Never read the project's .env here. Only INGESTION_TEST_DSN enables these tests.
"""

import uuid
from datetime import date, datetime, timezone

import pytest

from AI.orchestration.core import FetchRange


def commit(store, rows):
    span = FetchRange(date(2026, 9, 1), date(2026, 9, 2))
    run_id = store.start("crypto_daily", "BTC-USD", span, "test", {})
    raw_id = store.raw(
        run_id, "checksum", "test://payload", datetime.now(timezone.utc), {}
    )
    count = store.commit_bars(
        "crypto_daily",
        "BTC-USD",
        rows,
        run_id,
        raw_id,
        datetime.now(timezone.utc),
        [date(2026, 9, 1), date(2026, 9, 2)],
    )
    return run_id, count


def rows():
    return [
        {
            "date": f"2026-09-0{i}",
            "open": "10",
            "high": "12",
            "low": "9",
            "close": "11",
            "volume": "100",
        }
        for i in [1, 2]
    ]


def test_commit_idempotence_and_revision_history(store):
    store.register("crypto_daily", "BTC-USD", "24/7", date(2026, 9, 1))
    assert commit(store, rows())[1] == 2
    assert commit(store, rows())[1] == 0
    revised = rows()
    revised[0]["close"] = "12"
    assert commit(store, revised)[1] == 1
    assert store.query("SELECT count(*) AS n FROM ingestion.daily_bar")[0]["n"] == 3
    assert (
        store.query("SELECT count(*) AS n FROM public.crypto_price_data")[0]["n"] == 2
    )
    assert store.query(
        "SELECT last_success_event_time::date AS d FROM ingestion.dataset_watermark"
    )[0]["d"] == date(2026, 9, 2)


def test_commit_rollback_including_compatibility_table(store):
    store.register("crypto_daily", "BTC-USD", "24/7", date(2026, 9, 1))
    invalid = rows()
    invalid[1]["low"] = "100"
    with pytest.raises(Exception):
        commit(store, invalid)
    assert not store.observed("crypto_daily", "BTC-USD")
    assert not store.query("SELECT * FROM public.crypto_price_data")
    assert not store.query("SELECT * FROM ingestion.dataset_watermark")


def test_advisory_lock_and_recover_interrupted_run(store):
    run_id = store.start("crypto_daily", "BTC-USD", None, "test", {})
    with store.lock("crypto_daily", "BTC-USD") as first:
        assert first
        with store.lock("crypto_daily", "BTC-USD") as second:
            assert not second
    assert (
        store.query(
            "SELECT status FROM ingestion.dataset_run WHERE run_id=%s", (run_id,)
        )[0]["status"]
        == "interrupted"
    )
    with store.lock("crypto_daily", "BTC-USD") as third:
        assert third


def test_raw_lineage_foreign_key(store):
    with pytest.raises(Exception):
        store.raw(
            str(uuid.uuid4()),
            "checksum",
            "test://payload",
            datetime.now(timezone.utc),
            {},
        )


def test_migration_rerun_is_non_destructive(store, monkeypatch):
    from AI.orchestration import store as module

    with store.transaction() as cur:
        cur.execute("DROP SCHEMA ingestion CASCADE")
    monkeypatch.setattr(module, "connect", lambda _db: store.connection_factory())
    module.migrate("test")
    store.register("crypto_daily", "BTC-USD", "24/7", date(2026, 9, 1))
    module.migrate("test")
    assert len(store.partitions()) == 1


def test_runner_repairs_missing_compatibility_row(store, tmp_path):
    from AI.orchestration.archive import RawArchive
    from AI.orchestration.core import canonical_bytes
    from AI.orchestration.runner import run_partition

    class FixtureConnector:
        def fetch(self, ticker, span):
            return canonical_bytes(
                [
                    r
                    for r in rows()
                    if span.start <= date.fromisoformat(r["date"]) <= span.end
                ]
            )

        def parse(self, payload):
            import json

            return json.loads(payload)

    store.register(
        "crypto_daily", "BTC-USD", "24/7", date(2026, 9, 1), date(2026, 9, 2)
    )
    partition = store.partitions()[0]
    archive, connector = RawArchive(tmp_path), FixtureConnector()
    assert (
        run_partition(store, archive, connector, partition, "test")["status"]
        == "success"
    )
    with store.transaction() as cur:
        cur.execute("DELETE FROM public.crypto_price_data WHERE date='2026-09-01'")
    assert (
        run_partition(store, archive, connector, partition, "test")["status"]
        == "success"
    )
    assert (
        len(
            store.legacy_observed(
                "crypto_daily", "BTC-USD", date(2026, 9, 1), date(2026, 9, 2)
            )
        )
        == 2
    )
