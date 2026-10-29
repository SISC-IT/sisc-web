import copy
import json
from datetime import date

import pytest
from test_sec import CIK, NOW, companyfacts, submissions

from AI.orchestration.archive import RawArchive
from AI.orchestration.sec import collect_sec, parse_facts, parse_filings
from AI.orchestration.sec_validation import audit_cohort, load_cohort, source_index


class Client:
    def get(self, path, record, heartbeat):
        payload = submissions() if path.startswith("submissions") else companyfacts()
        raw = record(json.dumps(payload).encode(), {"path": path, "status": 200})
        return payload, raw


def cohort():
    return {
        "start_year": 2024,
        "end_year": 2025,
        "issuers": [
            {
                "cik": CIK,
                "label": "FIXTURE",
                "name": "Synthetic",
                "feature_model": "industrial",
            }
        ],
    }


def test_bounded_scope_does_not_import_or_validate_out_of_scope_facts():
    filings, _ = parse_filings(submissions(), CIK, "raw", NOW)
    payload = companyfacts()
    items = payload["facts"]["us-gaap"]["Revenues"]["units"]["USD"]
    items.append({**items[0], "end": "2012-12-31", "accn": "0000000001-12-000099"})
    _, errors = parse_facts(payload, CIK, filings, "raw")
    assert errors
    facts, errors = parse_facts(
        payload,
        CIK,
        filings,
        "raw",
        period_end_start=date(2017, 1, 1),
        period_end_end=date(2025, 12, 31),
    )
    assert len(facts) == 1 and not errors
    # An invalid/missing date cannot be guessed to lie outside the scope.
    items.append({**items[0], "end": "invalid"})
    _, errors = parse_facts(
        payload, CIK, filings, "raw", period_end_start=date(2017, 1, 1)
    )
    assert errors[0]["reason"] == "invalid_fact_period"


def test_cohort_source_audit_and_missing_year_are_explicit(store, tmp_path):
    assert (
        collect_sec(store, RawArchive(tmp_path), Client(), CIK, "test")["status"]
        == "success"
    )
    report = audit_cohort(store, cohort(), tmp_path)
    row = report["issuers"][0]
    assert report["fact_rows"] == 1 and report["technical_failure_issuers"] == 0
    assert row["source_failures"] == {} and row["boundary_checks"] == 1
    assert row["missing_annual_years"] == [2025]
    assert report["complete_annual_coverage_issuers"] == 0
    assert row["review_anchors"][0]["value"] == "100"
    assert report["golden_certification"] == "independent_accounting_review_pending"


def test_archive_tampering_is_failed_audit_not_clean_report(store, tmp_path):
    collect_sec(store, RawArchive(tmp_path), Client(), CIK, "test")
    row = store.query("SELECT object_uri FROM ingestion.raw_object LIMIT 1")[0]
    from pathlib import Path

    Path(row["object_uri"]).write_bytes(b"{}")
    report = audit_cohort(store, cohort(), tmp_path)
    assert report["technical_failure_issuers"] == 1
    assert report["issuers"][0]["audit_error"] == "ValueError"


def test_source_index_preserves_ambiguous_context_values():
    payload = companyfacts()
    items = payload["facts"]["us-gaap"]["Revenues"]["units"]["USD"]
    items.append({**items[0], "val": 999})
    assert next(iter(source_index(payload).values())) == {100, 999}


def test_default_cohort_has_50_distinct_identities_and_financial_exceptions(tmp_path):
    default = load_cohort()
    assert len(default["issuers"]) == 50
    assert (
        sum(
            r["feature_model"] == "not_applicable_financial" for r in default["issuers"]
        )
        == 2
    )
    bad = copy.deepcopy(default)
    bad["issuers"].append(bad["issuers"][0])
    path = tmp_path / "bad.json"
    path.write_text(json.dumps(bad), encoding="utf-8")
    with pytest.raises(ValueError):
        load_cohort(path)


def test_fiscal_year_ending_next_january_is_not_a_missing_year(store, tmp_path):
    class FiscalClient:
        def get(self, path, record, heartbeat):
            if path.startswith("submissions"):
                payload = submissions()
                payload["filings"]["recent"]["reportDate"] = ["2025-01-05"]
            else:
                payload = companyfacts()
                payload["facts"]["us-gaap"]["Revenues"]["units"]["USD"][0]["end"] = (
                    "2025-01-05"
                )
            return payload, record(json.dumps(payload).encode(), {"path": path})

    collect_sec(store, RawArchive(tmp_path), FiscalClient(), CIK, "test")
    report = audit_cohort(store, cohort(), tmp_path)
    assert report["issuers"][0]["missing_annual_years"] == [2025]
    assert report["issuers"][0]["coverage"][0]["annual_filing"]


def test_conflicting_source_fiscal_years_cannot_relabel_later_filing(store, tmp_path):
    class ConflictingClient:
        def get(self, path, record, heartbeat):
            if path.startswith("submissions"):
                payload = submissions()
                later = submissions(True)["filings"]["recent"]
                later["form"] = ["10-K"]
                later["reportDate"] = ["2023-12-31"]
                for key, values in later.items():
                    payload["filings"]["recent"][key].extend(values)
            else:
                payload = companyfacts()
                other = companyfacts(True)["facts"]["us-gaap"]["Revenues"]["units"][
                    "USD"
                ][0]
                other.update(start="2023-01-01", end="2023-12-31", form="10-K")
                payload["facts"]["us-gaap"]["Revenues"]["units"]["USD"].append(other)
            return payload, record(json.dumps(payload).encode(), {"path": path})

    assert (
        collect_sec(store, RawArchive(tmp_path), ConflictingClient(), CIK, "test")[
            "status"
        ]
        == "success"
    )
    report = audit_cohort(store, cohort(), tmp_path)
    row = report["issuers"][0]
    assert row["source_failures"]["ambiguous_annual_fiscal_year"] == 1
    assert row["missing_annual_years"] == [2024, 2025]
    assert row["coverage"][0]["features"] == []


def test_replay_adds_new_metric_preserves_keys_and_source_freshness(store, tmp_path):
    from test_sec import ACC

    from AI.orchestration.core import content_hash
    from AI.orchestration.metrics import METRIC_VERSIONS, METRICS
    from AI.orchestration.sec_replay import replay_sec

    class DebtClient:
        def get(self, path, record, heartbeat):
            payload = (
                submissions() if path.startswith("submissions") else companyfacts()
            )
            if "facts" in payload:
                payload["facts"]["us-gaap"]["LongTermDebt"] = {
                    "units": {
                        "USD": [
                            {
                                "accn": ACC,
                                "form": "10-K",
                                "end": "2024-12-31",
                                "val": 40,
                                "fy": 2024,
                                "fp": "FY",
                            }
                        ]
                    }
                }
            return payload, record(
                json.dumps(payload).encode(), {"path": path, "status": 200}
            )

    definition = METRICS.pop("long_term_debt_including_current")
    try:
        initial = collect_sec(store, RawArchive(tmp_path), DebtClient(), CIK, "v1")
    finally:
        METRICS["long_term_debt_including_current"] = definition
    old = store.query("SELECT fact_id,source_key FROM ingestion.fundamental_fact")[0]
    assert old["source_key"] == content_hash(
        [
            CIK,
            ACC,
            "revenue",
            "Revenues",
            "2024-01-01",
            "2024-12-31",
            "USD",
            "sec_us_gaap_v1",
        ]
    )
    watermark = store.query("SELECT * FROM ingestion.dataset_watermark")
    raw_count = store.query("SELECT count(*) AS n FROM ingestion.raw_object")[0]["n"]
    replay = replay_sec(store, initial["run_id"], tmp_path, "v2")
    assert replay["status"] == "success" and replay["inserted"] == 1
    assert store.query("SELECT * FROM ingestion.dataset_watermark") == watermark
    assert (
        store.query("SELECT count(*) AS n FROM ingestion.raw_object")[0]["n"]
        == raw_count
    )
    assert replay_sec(store, replay["run_id"], tmp_path, "v2")["inserted"] == 0
    added = store.query(
        "SELECT * FROM ingestion.fundamental_fact WHERE metric_id='long_term_debt_including_current'"
    )[0]
    assert (
        added["definition_version"]
        == METRIC_VERSIONS["long_term_debt_including_current"]
    )
    assert (
        store.query(
            "SELECT fact_id,source_key FROM ingestion.fundamental_fact WHERE metric_id='revenue'"
        )[0]
        == old
    )


def test_replay_tampered_archive_cannot_commit(store, tmp_path):
    from pathlib import Path

    from AI.orchestration.sec_replay import replay_sec

    initial = collect_sec(store, RawArchive(tmp_path), Client(), CIK, "v1")
    path = store.query("SELECT object_uri FROM ingestion.raw_object LIMIT 1")[0][
        "object_uri"
    ]
    Path(path).write_bytes(b"corrupt")
    assert (
        replay_sec(store, initial["run_id"], tmp_path, "v2")["status"] == "quarantined"
    )
    assert (
        store.query("SELECT count(*) AS n FROM ingestion.fundamental_fact")[0]["n"] == 1
    )
