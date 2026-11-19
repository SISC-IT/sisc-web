import copy
import json
import uuid
from datetime import datetime, timezone

import pytest
from test_sec import CIK, companyfacts, submissions

from AI.orchestration.archive import RawArchive
from AI.orchestration.pit_store import AsOfDataset
from AI.orchestration.reference import import_reference
from AI.orchestration.sec import collect_sec, parse_facts, parse_filings, utc


def commit_filing(store, amended=False):
    run = store.start("sec_companyfacts", CIK, None, "test", {})
    raw = store.raw(
        run,
        "sha",
        "test://raw",
        datetime.now(timezone.utc),
        {},
        source="sec",
        format="http-response-json-v1",
    )
    filings, bad = parse_filings(
        submissions(amended), CIK, raw, utc("2026-09-23T00:00:00Z")
    )
    facts, errors = parse_facts(companyfacts(amended), CIK, filings, raw)
    assert not bad and not errors
    return store.commit_sec(CIK, filings, facts, run)


def test_sec_filing_amendment_boundary_and_idempotence(store):
    api = AsOfDataset(store)
    assert commit_filing(store) == 1
    assert commit_filing(store) == 0
    assert commit_filing(store, True) == 1
    assert not api.fundamentals(CIK, as_of="2025-02-20T21:00:59Z")
    assert api.fundamentals(CIK, as_of="2025-02-20T21:01:00Z")[0]["value"] == 100
    assert api.fundamentals(CIK, as_of="2025-03-20T21:00:59Z")[0]["value"] == 100
    assert api.fundamentals(CIK, as_of="2025-03-20T21:01:00Z")[0]["value"] == 110
    with pytest.raises(TypeError):
        api.fundamentals(CIK)
    with pytest.raises(ValueError):
        api.fundamentals(CIK, as_of="2025-03-20")


def test_changed_sec_natural_key_is_not_backdated(store):
    commit_filing(store)
    run = store.start("sec_companyfacts", CIK, None, "test", {})
    raw = store.raw(run, "sha2", "test://raw2", datetime.now(timezone.utc), {})
    filings, _ = parse_filings(submissions(), CIK, raw, utc("2026-09-23T00:00:00Z"))
    payload = companyfacts()
    payload["facts"]["us-gaap"]["Revenues"]["units"]["USD"][0]["val"] = 999
    facts, _ = parse_facts(payload, CIK, filings, raw)
    with pytest.raises(ValueError):
        store.commit_sec(CIK, filings, facts, run)
    assert (
        AsOfDataset(store).fundamentals(CIK, as_of="2025-03-01T00:00:00Z")[0]["value"]
        == 100
    )


def test_silver_mutation_and_temporal_spoofing_rejected_by_database(store):
    commit_filing(store)
    with pytest.raises(Exception), store.transaction() as cur:
        cur.execute("UPDATE ingestion.fundamental_fact SET value=999")
    with pytest.raises(Exception), store.transaction() as cur:
        cur.execute(
            """INSERT INTO ingestion.fundamental_fact SELECT
            %s,cik,metric_id,filing_id,source_tag,tag_priority,period_start,period_end,
            reported_fy,reported_fp,value,unit,published_at-interval '1 day',
            available_at-interval '1 day',definition_version,'spoofed-key',raw_id,run_id,ingested_at
            FROM ingestion.fundamental_fact LIMIT 1""",
            (str(uuid.uuid4()),),
        )


def record(
    instrument, kind, key, start, payload, available="2025-01-01T00:00:00Z", end=None
):
    return {
        "instrument_id": instrument,
        "kind": kind,
        "source": "synthetic_fixture",
        "source_key": key,
        "published_at": available,
        "available_at": available,
        "valid_from": start,
        "valid_to": end,
        "payload": payload,
    }


def instrument(identity):
    return {
        "instrument_id": identity,
        "name": "Synthetic company",
        "asset_type": "equity",
        "currency": "USD",
        "exchange": "XNYS",
        "timezone": "America/New_York",
    }


def imported(store, tmp_path, document):
    return import_reference(
        store, RawArchive(tmp_path), json.dumps(document).encode(), "test"
    )


def test_ticker_change_reuse_and_delisting_history(store, tmp_path):
    first, second = str(uuid.uuid4()), str(uuid.uuid4())
    document = {
        "instruments": [instrument(first), instrument(second)],
        "records": [
            record(
                first,
                "identifier",
                "old",
                "2025-01-01",
                {"identifier_type": "ticker", "value": "OLD", "venue": "XNYS"},
                end="2025-02-01",
            ),
            record(
                first,
                "identifier",
                "new",
                "2025-02-01",
                {"identifier_type": "ticker", "value": "NEW", "venue": "XNYS"},
            ),
            record(
                second,
                "identifier",
                "reuse",
                "2025-02-01",
                {"identifier_type": "ticker", "value": "OLD", "venue": "XNYS"},
            ),
            record(
                first, "membership", "member", "2025-01-01", {"universe_id": "test"}
            ),
        ],
    }
    assert imported(store, tmp_path, document)["status"] == "success"
    api = AsOfDataset(store)
    assert (
        api.resolve(
            "ticker", "OLD", on="2025-01-15", as_of="2025-01-15T12:00:00Z", venue="XNYS"
        )
        == first
    )
    assert (
        api.resolve(
            "ticker", "NEW", on="2025-02-15", as_of="2025-02-15T12:00:00Z", venue="XNYS"
        )
        == first
    )
    assert (
        api.resolve(
            "ticker", "OLD", on="2025-02-15", as_of="2025-02-15T12:00:00Z", venue="XNYS"
        )
        == second
    )
    update = copy.deepcopy(document["records"][-1])
    update.update(
        available_at="2025-02-01T00:00:00Z",
        published_at="2025-02-01T00:00:00Z",
        valid_to="2025-03-01",
    )
    assert imported(store, tmp_path, {"records": [update]})["status"] == "success"
    assert api.universe("test", on="2025-04-01", as_of="2025-01-15T00:00:00Z") == [
        first
    ]
    assert api.universe("test", on="2025-04-01", as_of="2025-04-01T00:00:00Z") == []
    assert api.universe("test", on="2025-02-15", as_of="2025-04-01T00:00:00Z") == [
        first
    ]


def test_split_dividend_raw_invariance_and_source_lineage(store, tmp_path):
    identity = str(uuid.uuid4())
    records = []
    for day, close in [("2025-02-03", "100"), ("2025-02-04", "50")]:
        records.append(
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
        )
    records.append(
        record(
            identity,
            "corporate_action",
            "split",
            "2025-02-04",
            {"action_type": "split", "ratio_numerator": "2", "ratio_denominator": "1"},
        )
    )
    records.append(
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
        )
    )
    assert (
        imported(
            store, tmp_path, {"instruments": [instrument(identity)], "records": records}
        )["status"]
        == "success"
    )
    api = AsOfDataset(store)
    args = {"start": "2025-02-03", "end": "2025-02-04", "as_of": "2025-02-05T00:00:00Z"}
    assert [r["close"] for r in api.bars(identity, basis="raw", **args)] == [100, 50]
    adjusted = api.bars(identity, basis="split_adjusted", **args)
    assert [r["close"] for r in adjusted] == [50, 50] and adjusted[0]["volume"] == 20
    total = api.bars(identity, basis="total_return", **args)
    assert [r["total_return_index"] for r in total] == [100, 102]
    assert len(total[-1]["source_record_ids"]) == 4


def test_sec_collect_archives_and_rejects_empty_payload(store, tmp_path):
    class Client:
        def __init__(self, empty=False):
            self.empty = empty

        def get(self, path, record, heartbeat):
            payload = (
                submissions()
                if path.startswith("submissions")
                else ({"cik": 1, "facts": {}} if self.empty else companyfacts())
            )
            raw = record(json.dumps(payload).encode(), {"path": path, "status": 200})
            return payload, raw

    archive = RawArchive(tmp_path)
    assert collect_sec(store, archive, Client(), CIK, "test")["status"] == "success"
    before = store.query("SELECT * FROM ingestion.dataset_watermark")[0]
    assert (
        collect_sec(store, archive, Client(True), CIK, "test")["status"]
        == "quarantined"
    )
    after = store.query("SELECT * FROM ingestion.dataset_watermark")[0]
    assert before == after
    assert store.query("SELECT count(*) AS n FROM ingestion.raw_object")[0]["n"] == 4
