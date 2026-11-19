import copy
import uuid
from datetime import date, datetime, timedelta, timezone

import pytest
from test_fred import observation
from test_pit_postgres import commit_filing, imported, instrument, record
from test_sec import CIK
from test_series_postgres import collect

from AI.orchestration.consistent import consistent_read
from AI.orchestration.pit_store import AsOfDataset
from AI.orchestration.quality import run_quality
from AI.orchestration.snapshot import (
    baseline,
    export_snapshot,
    materialize,
    verify_snapshot,
)


def test_zero_success_incident_dedup_and_recovery(store):
    run = store.start("fixture", "all", None, "test", {})
    store.finish(run, "success", fetched=0)
    first = run_quality(store, "test")
    assert any(r["rule"] == "nonempty_success" for r in first["failures"])
    assert first["alerts"]
    assert run_quality(store, "test")["alerts"] == []
    good = store.start("fixture", "all", None, "test", {})
    store.finish(good, "success", fetched=3)
    recovered = run_quality(store, "test")
    assert any(
        r["rule"] == "nonempty_success" and r["event"] == "resolved"
        for r in recovered["alerts"]
    )
    assert any(r["rule"] == "success_ratio_7d" for r in recovered["failures"])
    with pytest.raises(Exception):
        with store.transaction() as cur:
            cur.execute("DELETE FROM ingestion.dq_result")


def test_never_run_stalled_and_calendar_gaps(store):
    store.register("crypto_daily", "BTC-USD", "24/7", date(2026, 9, 19))
    run = store.start("fixture", "all", None, "test", {})
    with store.transaction() as cur:
        cur.execute(
            "UPDATE ingestion.dataset_run SET heartbeat_at='2026-09-23T11:00:00Z' WHERE run_id=%s",
            (run,),
        )
    result = run_quality(
        store, "test", now=datetime(2026, 9, 23, 12, tzinfo=timezone.utc)
    )
    assert any(r["rule"] == "worker_heartbeat" for r in result["failures"])
    gap = next(r for r in result["failures"] if r["rule"] == "session_completeness")
    assert gap["details"]["missing"] == 4
    assert any(r["rule"] == "collection_freshness" for r in result["failures"])


def test_no_registered_data_is_not_healthy(store):
    result = run_quality(store, "test")
    assert result["failures"][0]["rule"] == "registered_or_observed_dataset"


def test_repeatable_read_does_not_mix_concurrent_ingestion(store):
    with consistent_read(store) as view:
        assert view.query("SELECT count(*) AS n FROM ingestion.sec_issuer")[0]["n"] == 0
        store.register_issuer(CIK)
        assert view.query("SELECT count(*) AS n FROM ingestion.sec_issuer")[0]["n"] == 0
    assert store.query("SELECT count(*) AS n FROM ingestion.sec_issuer")[0]["n"] == 1


def spec(store, queries):
    return {
        "data_as_of": store.query("SELECT clock_timestamp() AS t")[0]["t"].isoformat(),
        "queries": queries,
    }


def test_snapshot_macro_hash_parquet_and_tampering(store, tmp_path):
    collect(store, tmp_path / "bronze", [observation()])
    query = {
        "name": "gdp",
        "kind": "series",
        "args": {
            "ids": ["GDP"],
            "start": "2020-01-01",
            "end": "2020-12-31",
            "as_of": "2020-05-01T00:00:00Z",
        },
    }
    request = spec(store, [query])
    one = export_snapshot(store, request, tmp_path, "one", "test")
    two = export_snapshot(store, request, tmp_path, "two", "test")
    assert one["content_hash"] == two["content_hash"]
    manifest, rows = verify_snapshot(
        one["directory"], expected_hash=one["content_hash"]
    )
    assert rows["gdp"][0]["value"] == "100"
    assert manifest["source_lineage"]
    with pytest.raises(FileExistsError):
        export_snapshot(store, request, tmp_path, "one", "test")
    with pytest.raises(ValueError):
        verify_snapshot(one["directory"], expected_hash="wrong")
    data = tmp_path / "one" / "data.json"
    data.write_text("{}", encoding="utf-8")
    with pytest.raises(ValueError, match="checksum"):
        verify_snapshot(one["directory"])


def test_data_cutoff_hides_later_collected_amendment(store):
    commit_filing(store)
    cutoff = store.query("SELECT clock_timestamp() AS t")[0]["t"]
    commit_filing(store, True)
    fixed = AsOfDataset(store, data_as_of=cutoff)
    assert fixed.fundamentals(CIK, as_of="2025-04-01T00:00:00Z")[0]["value"] == 100
    assert (
        AsOfDataset(store).fundamentals(CIK, as_of="2025-04-01T00:00:00Z")[0]["value"]
        == 110
    )


def test_snapshot_empty_and_temporal_contract(store, tmp_path):
    store.register_issuer(CIK)
    query = {
        "name": "facts",
        "kind": "fundamentals",
        "args": {"cik": CIK, "as_of": "2025-01-01T00:00:00Z"},
    }
    request = spec(store, [query])
    with pytest.raises(ValueError, match="Empty"):
        materialize(store, request)
    query["allow_empty"] = True
    assert materialize(store, request)[0] == {"facts": []}
    request["data_as_of"] = "2024-01-01T00:00:00Z"
    with pytest.raises(ValueError):
        materialize(store, request)
    request["data_as_of"] = (datetime.now(timezone.utc) + timedelta(days=1)).isoformat()
    with pytest.raises(ValueError):
        materialize(store, request)


def test_snapshot_cutoff_uses_database_clock_when_client_clock_is_slow(
    store, monkeypatch
):
    from AI.orchestration import snapshot

    class SlowClock:
        @staticmethod
        def now(tz):
            return datetime(2000, 1, 1, tzinfo=tz)

    request = spec(
        store,
        [
            {
                "name": "empty",
                "kind": "fundamentals",
                "allow_empty": True,
                "args": {"cik": CIK, "as_of": "2025-01-01T00:00:00Z"},
            }
        ],
    )
    monkeypatch.setattr(snapshot, "datetime", SlowClock)
    assert materialize(store, request)[0] == {"empty": []}


def price_fixture(store, tmp_path):
    identity = str(uuid.uuid4())
    records = [
        record(
            identity,
            "bar",
            day,
            day,
            {
                "open": close,
                "high": close,
                "low": close,
                "close": close,
                "volume": "10",
                "price_basis": "raw",
                "currency": "USD",
                "session_close": day + "T21:00:00Z",
            },
            day + "T21:01:00Z",
        )
        for day, close in [("2025-02-03", "100"), ("2025-02-04", "50")]
    ]
    records.extend(
        [
            record(
                identity,
                "corporate_action",
                "split",
                "2025-02-04",
                {
                    "action_type": "split",
                    "ratio_numerator": "2",
                    "ratio_denominator": "1",
                },
            ),
            record(
                identity,
                "corporate_action",
                "dividend",
                "2025-02-04",
                {
                    "action_type": "cash_dividend",
                    "cash_amount": "1",
                    "currency": "USD",
                    "share_basis": "post_split",
                },
            ),
        ]
    )
    document = {"instruments": [instrument(identity)], "records": records}
    assert imported(store, tmp_path, document)["status"] == "success"
    return identity, document


def test_offline_strategy_replays_after_live_revision(store, tmp_path):
    identity, document = price_fixture(store, tmp_path / "bronze")
    request = spec(
        store,
        [
            {
                "name": "prices",
                "kind": "bars",
                "args": {
                    "instrument_id": identity,
                    "start": "2025-02-03",
                    "end": "2025-02-04",
                    "basis": "total_return",
                    "as_of": "2025-02-06T00:00:00Z",
                },
            }
        ],
    )
    exported = export_snapshot(store, request, tmp_path, "frozen", "test")
    original = baseline(exported["directory"], "prices")
    assert original["total_return"] == "0.02"
    revised = copy.deepcopy(document["records"][1])
    revised["available_at"] = revised["published_at"] = "2025-02-05T21:01:00Z"
    for k in ("open", "high", "low", "close"):
        revised["payload"][k] = "55"
    imported(store, tmp_path / "bronze", {"instruments": [], "records": [revised]})
    assert baseline(exported["directory"], "prices") == original
    second = export_snapshot(store, request, tmp_path, "same-cutoff", "test")
    assert second["content_hash"] == exported["content_hash"]
    request["data_as_of"] = store.query("SELECT clock_timestamp() AS t")[0][
        "t"
    ].isoformat()
    changed = export_snapshot(store, request, tmp_path, "new-cutoff", "test")
    assert baseline(changed["directory"], "prices")["total_return"] == "0.12"
    # Parquet is independently verified, not merely an unused side artifact.
    parquet = tmp_path / "frozen" / "data.parquet"
    parquet.write_bytes(b"broken")
    with pytest.raises(ValueError, match="checksum"):
        baseline(exported["directory"], "prices")


def test_snapshot_registration_recovery_and_identity_conflict(
    store, tmp_path, monkeypatch
):
    from AI.orchestration.snapshot import register_snapshot

    collect(store, tmp_path / "bronze", [observation()])
    request = spec(
        store,
        [
            {
                "name": "gdp",
                "kind": "series",
                "args": {
                    "ids": ["GDP"],
                    "start": "2020-01-01",
                    "end": "2020-01-01",
                    "as_of": "2020-05-01T00:00:00Z",
                },
            }
        ],
    )
    original = store.transaction

    def unavailable():
        raise RuntimeError("simulated registry outage")

    monkeypatch.setattr(store, "transaction", unavailable)
    with pytest.raises(RuntimeError, match="registry outage"):
        export_snapshot(store, request, tmp_path, "recoverable", "test")
    manifest, _ = verify_snapshot(tmp_path / "recoverable")
    monkeypatch.setattr(store, "transaction", original)
    assert register_snapshot(store, tmp_path / "recoverable") == "recoverable"
    assert register_snapshot(store, tmp_path / "recoverable") == "recoverable"
    with pytest.raises(ValueError, match="already registered"):
        export_snapshot(store, request, tmp_path / "other", "recoverable", "test")
    saved = store.query("SELECT * FROM ingestion.dataset_snapshot")
    assert len(saved) == 1 and saved[0]["content_hash"] == manifest["content_hash"]
    with pytest.raises(Exception):
        with store.transaction() as cur:
            cur.execute("DELETE FROM ingestion.dataset_snapshot")
