import copy
import hashlib
import json
from datetime import date

import pytest
from test_features import fact
from test_sec import CIK, companyfacts, submissions
from test_sec_validation import Client, cohort

from AI.orchestration.archive import RawArchive
from AI.orchestration.features import financial_features, ttm
from AI.orchestration.sec import collect_sec
from AI.orchestration.sec_replay import replay_sec
from AI.orchestration.sec_validation import audit_cohort
from AI.orchestration.source_reviews import REVIEWS, debt_review, fiscal_year_review


def feed_payload():
    return b"""<SUBMISSION>
<ACCESSION-NUMBER>0000000001-24-000001
<TYPE>10-K
<PERIOD>20241231
<FILING-DATE>20250220
<FILER>
<CIK>0000000001
</FILER>
<DOCUMENT>
<TYPE>10-K
<FILENAME>annual.htm
<TEXT>synthetic body"""


def test_feed_date_bound_is_explicit_and_identity_is_checked():
    from AI.orchestration.sec_feed import parse_feed_submission

    url = "https://www.sec.gov/Archives/edgar/Feed/2025/QTR1/20250220.nc.tar.gz"
    row = parse_feed_submission(feed_payload(), url, "./0000000001-24-000001.nc")
    assert row["accepted_at"] is None
    assert row["available_at"].isoformat() == "2025-02-21T05:01:00+00:00"
    assert row["availability_basis"] == "sec_feed_day_end"
    with pytest.raises(ValueError):
        parse_feed_submission(
            feed_payload(),
            url.replace("20250220", "20250221"),
            "./0000000001-24-000001.nc",
        )


def test_feed_recovery_preserves_unknown_timestamp_and_pit_boundary(store, tmp_path):
    from datetime import timedelta

    from AI.orchestration.pit_store import AsOfDataset
    from AI.orchestration.sec import parse_facts, utc
    from AI.orchestration.sec_feed import recovered_filings, register_feed_submission

    archive = RawArchive(tmp_path)
    register_feed_submission(
        store,
        archive,
        feed_payload(),
        "https://www.sec.gov/Archives/edgar/Feed/2025/QTR1/20250220.nc.tar.gz",
        "./0000000001-24-000001.nc",
        "test",
    )
    filing = recovered_filings(store, CIK, tmp_path)[0]
    run = store.start("sec_replay", CIK, None, "test", {})
    payload = companyfacts()
    items = payload["facts"]["us-gaap"]["Revenues"]["units"]["USD"]
    items[0]["accn"] = filing["accession_number"]
    checksum, path = archive.put(json.dumps(payload).encode())
    raw = store.raw(
        run,
        checksum,
        path,
        utc("2025-03-01T00:00:00Z"),
        {},
        source="sec",
        format="http-response-json-v1",
    )
    facts, errors = parse_facts(payload, CIK, [filing], raw)
    assert not errors
    store.commit_sec(CIK, [filing], facts, run, advance_watermark=False)
    api = AsOfDataset(store)
    assert not api.fundamentals(
        CIK, as_of=filing["available_at"] - timedelta(microseconds=1)
    )
    visible = api.fundamentals(CIK, as_of=filing["available_at"])
    assert visible[0]["published_at"] is None
    assert visible[0]["availability_basis"] == "sec_feed_day_end"
    assert not store.query("SELECT * FROM ingestion.dataset_watermark")


def output(rows):
    return {
        f["metric_id"]: f
        for f in financial_features(
            rows, as_of="2026-10-01T00:00:00Z", industry_model="industrial"
        )
    }


def test_reviewed_debt_resolution_is_exact_and_does_not_mask_revisions():
    review = REVIEWS["debt"][1]
    rows = [
        dict(
            fact(m, review["period_end"], review["period_end"], v),
            cik=review["cik"],
            filing_id="same-filing",
        )
        for m, v in review["expected"].items()
    ]
    assert output(rows)["total_debt"]["value"] == 57791000000
    assert (
        output(rows)["total_debt"]["source_review"]["reason"]
        == "face_value_versus_carrying_value"
    )
    rows[-1]["value"] += 1
    assert output(rows)["total_debt"]["status"] == "conflicting_debt_totals"
    rows[-1]["value"] -= 1
    rows[-1]["filing_id"] = "another-revision"
    assert debt_review(rows, date(2024, 12, 31)) is None


def test_reviewed_refinancing_does_not_double_count_current_maturities():
    review = next(
        r
        for r in REVIEWS["debt"]
        if r["cik"] == "0000093410" and r["period_end"] == "2019-12-31"
    )
    rows = [
        dict(
            fact(m, review["period_end"], review["period_end"], v),
            cik=review["cik"],
            filing_id="same-filing",
        )
        for m, v in review["expected"].items()
    ]
    rows.append(
        dict(
            fact("current_long_term_debt", "2019-12-31", "2019-12-31", "5054000000"),
            cik=review["cik"],
            filing_id="same-filing",
        )
    )
    result = output(rows)["total_debt"]
    assert result["value"] == 26673000000  # 3282 + 23691 - 282 - 18, USD million.
    assert result["source_review"]["resolution"].startswith("reviewed_balance_sheet")


def test_comparative_carrying_values_can_reconcile_older_principal_disclosure():
    review = next(
        r
        for r in REVIEWS["debt"]
        if r["cik"] == "0001551152" and r["period_end"] == "2020-12-31"
    )
    rows = [
        dict(
            fact(m, review["period_end"], review["period_end"], v),
            cik=review["cik"],
            filing_id="old-principal"
            if m == "long_term_debt_including_current"
            else "comparative-balance",
        )
        for m, v in review["expected"].items()
    ]
    assert output(rows)["total_debt"]["value"] == 86027000000
    rows[0]["value"] += 1
    assert output(rows)["total_debt"]["status"] == "conflicting_debt_totals"


def test_explicit_components_recover_pretax_and_lease_excluded_debt():
    rows = [
        fact(m, "2024-01-01", "2024-12-31", v)
        for m, v in [
            ("income_before_tax_domestic", "80"),
            ("income_before_tax_foreign", "20"),
            ("interest_expense", "10"),
        ]
    ]
    rows += [
        fact(m, "2024-12-31", "2024-12-31", v)
        for m, v in [
            ("total_assets", "500"),
            ("debt_current_including_leases", "30"),
            ("current_debt_and_finance_leases", "25"),
            ("finance_lease_current", "5"),
            ("noncurrent_debt_and_finance_leases", "100"),
            ("finance_lease_noncurrent", "10"),
            ("cash_and_equivalents", "20"),
        ]
    ]
    assert output(rows)["ebit"]["value"] == 110
    assert output(rows)["total_debt"]["value"] == 115
    assert output(rows)["net_debt"]["value"] == 95
    without_lease = [f for f in rows if f["metric_id"] != "finance_lease_current"]
    assert output(without_lease)["total_debt"]["value"] is None
    assert (
        "finance_lease_current"
        in output(without_lease)["total_debt"]["missing_metrics"]
    )


def test_old_flow_cannot_fill_current_balance_period():
    rows = [fact("operating_cash_flow", "2023-01-01", "2023-12-31", "100")]
    assert (
        ttm(
            rows,
            "operating_cash_flow",
            as_of="2025-03-01T00:00:00Z",
            period_end=date(2024, 12, 31),
        )["status"]
        == "missing_target_period"
    )


def test_nonoperating_interest_fallback_is_explicit_and_not_net_interest():
    rows = [
        fact("income_before_tax", "2024-01-01", "2024-12-31", "100"),
        fact("interest_expense_nonoperating", "2024-01-01", "2024-12-31", "10"),
    ]
    ebit = output(rows)["ebit"]
    assert ebit["value"] == 110
    assert ebit["calculation_version"] == "ebit_pretax_plus_nonoperating_interest_v3"


def test_review_fiscal_year_requires_exact_source_and_checksum(tmp_path, monkeypatch):
    from AI.orchestration import source_reviews

    review = copy.deepcopy(REVIEWS["fiscal_year"][0])
    payload = b"synthetic reviewed cover year"
    (tmp_path / "evidence.html").write_bytes(payload)
    review["evidence"] = {
        "archive_path": "evidence.html",
        "sha256": hashlib.sha256(payload).hexdigest(),
    }
    monkeypatch.setattr(source_reviews, "REVIEWS", {"fiscal_year": [review]})
    filing = {
        "cik": review["cik"],
        "accession_number": review["accession"],
        "period_end": date.fromisoformat(review["period_end"]),
    }
    assert fiscal_year_review(filing, {2020}, tmp_path)["reviewed_fy"] == 2021
    assert fiscal_year_review(filing, {2019}, tmp_path) is None
    (tmp_path / "evidence.html").write_bytes(b"tampered")
    with pytest.raises(ValueError):
        fiscal_year_review(filing, {2020}, tmp_path)


def test_isolated_unlinked_fact_preserves_valid_facts_without_advancing_watermark(
    store, tmp_path
):
    class MissingFilingClient:
        def get(self, path, record, heartbeat):
            payload = (
                submissions() if path.startswith("submissions") else companyfacts()
            )
            if not path.startswith("submissions"):
                items = payload["facts"]["us-gaap"]["Revenues"]["units"]["USD"]
                items.append({**items[0], "accn": "0000000001-24-000999"})
            return payload, record(
                json.dumps(payload).encode(), {"path": path, "status": 200}
            )

    original = collect_sec(
        store, RawArchive(tmp_path), MissingFilingClient(), CIK, "test"
    )
    assert original["status"] == "quarantined"
    result = replay_sec(
        store, original["run_id"], tmp_path, "test", isolate_unlinked_filings=True
    )
    assert result["status"] == "quarantined" and result["inserted"] == 1
    assert result["isolated_rejections"] == 1
    assert not store.query("SELECT * FROM ingestion.dataset_watermark")
    assert len(store.query("SELECT * FROM ingestion.fundamental_fact")) == 1
    replay = replay_sec(
        store, result["run_id"], tmp_path, "test", isolate_unlinked_filings=True
    )
    assert replay["inserted"] == 0
    assert audit_cohort(store, cohort(), tmp_path)["technical_failure_issuers"] == 1


def test_post_acquisition_year_is_not_missing_but_not_eight_year_complete(
    store, tmp_path
):
    collect_sec(store, RawArchive(tmp_path), Client(), CIK, "test")
    config = cohort()
    config["issuers"][0].update(
        not_applicable_years=[2025],
        reporting_end_evidence={
            "acquired_at": "2025-06-01",
            "url": "https://www.sec.gov/example",
        },
    )
    result = audit_cohort(store, config, tmp_path)
    assert result["complete_expected_annual_coverage_issuers"] == 1
    assert result["complete_annual_coverage_issuers"] == 0
    assert result["issuers"][0]["missing_annual_years"] == [2025]
