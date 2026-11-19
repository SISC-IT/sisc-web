"""SEC cohort evidence: archived-source reconciliation, coverage and PIT boundaries.

Source reconciliation is not an independent accounting/taxonomy certification.
Missing filings and unsupported industry features remain explicit failures/gaps.
"""

import hashlib
import json
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

from AI.orchestration.consistent import consistent_read
from AI.orchestration.core import content_hash
from AI.orchestration.features import financial_features
from AI.orchestration.metrics import MAPPING_VERSION, METRICS
from AI.orchestration.pit_store import AsOfDataset
from AI.orchestration.sec import cik_string, utc
from AI.orchestration.snapshot import normalize, source_tree_hash
from AI.orchestration.source_reviews import fiscal_year_review, verify_evidence

DEFAULT_COHORT = Path(__file__).with_name("sec50_cohort.json")


def load_cohort(path=DEFAULT_COHORT):
    cohort = json.loads(Path(path).read_text(encoding="utf-8-sig"))
    if not 1900 <= cohort["start_year"] <= cohort["end_year"] <= datetime.now().year:
        raise ValueError("Invalid validation years")
    ids = [cik_string(i["cik"]) for i in cohort["issuers"]]
    if not ids or len(ids) != len(set(ids)):
        raise ValueError("Cohort must contain unique issuers")
    for item, cik in zip(cohort["issuers"], ids):
        item["cik"] = cik
        if item["feature_model"] not in {"industrial", "not_applicable_financial"}:
            raise ValueError("Explicit supported feature model required")
    return cohort


class Evidence:
    def __init__(self, store, archive_root):
        self.store = store
        self.root = Path(archive_root).resolve()
        self.cache = {}
        self.hashes = {}

    def load(self, raw_id):
        key = str(raw_id)
        if key not in self.cache:
            row = self.store.query(
                "SELECT * FROM ingestion.raw_object WHERE raw_id=%s", (key,)
            )[0]
            path = Path(row["object_uri"]).resolve()
            if not path.is_relative_to(self.root):
                raise ValueError("Source archive is outside configured root")
            payload = path.read_bytes()
            if hashlib.sha256(payload).hexdigest() != row["checksum"]:
                raise ValueError("Source archive checksum mismatch")
            if row["format"] == "sec-feed-submission-v1":
                from AI.orchestration.sec_feed import parse_feed_submission

                self.cache[key] = {
                    "feed_filing": parse_feed_submission(
                        payload,
                        row["request_metadata"]["url"],
                        row["request_metadata"]["member"],
                        raw_id,
                    )
                }
            else:
                self.cache[key] = json.loads(payload, parse_float=Decimal)
            self.hashes[key] = row["checksum"]
        return self.cache[key]


def source_index(payload):
    """Independent lookup in original companyfacts, without calling ingestion parser."""
    result = defaultdict(set)
    for tag, definition in payload.get("facts", {}).get("us-gaap", {}).items():
        for unit, items in definition.get("units", {}).items():
            for item in items:
                key = (
                    tag,
                    unit,
                    item.get("accn"),
                    item.get("start", item["end"]),
                    item["end"],
                )
                result[key].add(Decimal(str(item["val"])))
    return result


def audit_issuer(store, issuer, years, archive_root):
    cik = issuer["cik"]
    api = AsOfDataset(store)
    facts = store.query(
        """SELECT f.*,p.accession_number,p.form_type FROM ingestion.fundamental_fact f
        JOIN ingestion.filing p USING(filing_id) WHERE f.cik=%s ORDER BY f.period_end,f.metric_id,f.source_key""",
        (cik,),
    )
    filings = store.query(
        "SELECT * FROM ingestion.filing WHERE cik=%s ORDER BY available_at", (cik,)
    )
    latest = store.query(
        """SELECT run_id,status,fetched_count,inserted_count FROM ingestion.dataset_run
        WHERE dataset_id='sec_companyfacts' AND partition_key=%s ORDER BY started_at DESC LIMIT 1""",
        (cik,),
    )
    quarantine = store.query(
        """SELECT q.reasons FROM ingestion.quarantine q
        JOIN ingestion.dataset_run r USING(run_id) WHERE r.partition_key=%s AND r.dataset_id='sec_companyfacts'
        ORDER BY q.created_at DESC LIMIT 1""",
        (cik,),
    )
    evidence, failures = Evidence(store, archive_root), Counter()
    source_values, accepted = {}, {}
    # Original submissions provide the timing oracle, not Silver's own timestamps.
    for filing in filings:
        payload = evidence.load(filing["raw_id"])
        if "feed_filing" in payload:
            source = payload["feed_filing"]
            if any(
                filing[k] != source[k]
                for k in (
                    "cik",
                    "accession_number",
                    "filed_date",
                    "accepted_at",
                    "available_at",
                    "availability_basis",
                )
            ):
                failures["filing_timestamp_mismatch"] += 1
            continue
        raw_key = str(filing["raw_id"])
        if raw_key not in accepted:
            columns = payload.get("filings", {}).get("recent", payload)
            accepted[raw_key] = dict(
                zip(columns["accessionNumber"], columns["acceptanceDateTime"])
            )
        original = accepted[raw_key].get(filing["accession_number"])
        if (
            not original
            or utc(original) != filing["accepted_at"]
            or filing["available_at"] < utc(original)
        ):
            failures["filing_timestamp_mismatch"] += 1
    parents = {str(f["filing_id"]): f for f in filings}
    for fact in facts:
        raw_key = str(fact["raw_id"])
        if raw_key not in source_values:
            payload = evidence.load(fact["raw_id"])
            if cik_string(payload["cik"]) != cik:
                failures["source_cik_mismatch"] += 1
            source_values[raw_key] = source_index(payload)
        key = (
            fact["source_tag"],
            fact["unit"],
            fact["accession_number"],
            str(fact["period_start"]),
            str(fact["period_end"]),
        )
        values = source_values[raw_key].get(key, set())
        if values != {fact["value"]}:
            failures["source_value_or_context_mismatch"] += 1
        definition = METRICS.get(fact["metric_id"])
        if (
            not definition
            or fact["unit"] != definition[1]
            or fact["source_tag"] not in definition[2]
        ):
            failures["mapping_contract_mismatch"] += 1
        parent = parents[str(fact["filing_id"])]
        if (
            fact["available_at"] < parent["available_at"]
            or fact["published_at"] != parent["accepted_at"]
        ):
            failures["fact_temporal_mismatch"] += 1
    # 52/53-week fiscal years may end in January of the next calendar year.
    # Use the source FY attached to that filing's own report period, never a
    # calendar-year guess or FY from a later comparative filing.
    fiscal_years = defaultdict(set)
    for fact in facts:
        parent = parents[str(fact["filing_id"])]
        if (
            fact["period_end"] == parent["period_end"]
            and fact["reported_fy"] is not None
        ):
            fiscal_years[str(fact["filing_id"])].add(fact["reported_fy"])
    reviewed_years = []
    for filing in filings:
        review = fiscal_year_review(
            filing, fiscal_years[str(filing["filing_id"])], archive_root
        )
        if review:
            fiscal_years[str(filing["filing_id"])] = {review["reviewed_fy"]}
            reviewed_years.append(review)
    coverage, boundaries, samples, fiscal_conflicts = [], 0, [], {}
    for year in years:
        annual = [
            f
            for f in filings
            if f["form_type"] == "10-K"
            and f["period_end"]
            and fiscal_years[str(f["filing_id"])] == {year}
        ]
        annual.sort(key=lambda f: (f["period_end"], -f["available_at"].timestamp()))
        if len({f["period_end"] for f in annual}) > 1:
            # Never relabel the later report as an earlier fiscal year merely
            # because the source FY fields conflict.
            fiscal_conflicts[str(year)] = [
                {"accession": f["accession_number"], "period_end": f["period_end"]}
                for f in annual
            ]
            failures["ambiguous_annual_fiscal_year"] += 1
            annual = []
        period_facts = [f for f in facts if f["reported_fy"] == year]
        entry = {
            "year": year,
            "year_basis": "source_reported_fiscal_year",
            "fact_rows": len(period_facts),
            "metrics": sorted({f["metric_id"] for f in period_facts}),
            "annual_filing": bool(annual),
            "features": [],
        }
        if annual:
            filing = annual[-1]
            cutoff = filing["available_at"]
            before = api.fundamentals(cik, as_of=cutoff - timedelta(microseconds=1))
            after = api.fundamentals(cik, as_of=cutoff)
            boundaries += 1
            if any(
                str(f["filing_id"]) == str(filing["filing_id"])
                or f["available_at"] >= cutoff
                for f in before
            ):
                failures["pre_publication_exposure"] += 1
            if any(f["available_at"] > cutoff for f in after):
                failures["future_fact_exposure"] += 1
            visible = [
                f for f in after if str(f["filing_id"]) == str(filing["filing_id"])
            ]
            if not visible:
                failures["annual_filing_has_no_visible_mapped_facts"] += 1
            entry["as_of"] = cutoff
            entry["accession"] = filing["accession_number"]
            entry["availability_basis"] = filing.get(
                "availability_basis", "accepted_plus_delay"
            )
            if issuer["feature_model"] == "industrial":
                entry["features"] = financial_features(
                    after, as_of=cutoff, industry_model="industrial"
                )
                for value in entry["features"]:
                    if value.get("source_review"):
                        verify_evidence(value["source_review"], archive_root)
                    if (
                        value["available_at"] is not None
                        and value["available_at"] > cutoff
                    ):
                        failures["future_feature_exposure"] += 1
            else:
                entry["feature_status"] = "not_applicable_financial"
            # Small reviewable anchors include raw identity, units, period and accession.
            for metric in (
                "revenue",
                "net_income",
                "total_assets",
                "operating_cash_flow",
                "capital_expenditure",
            ):
                candidates = [
                    f
                    for f in visible
                    if f["metric_id"] == metric
                    and f["period_end"] == filing["period_end"]
                ]
                if candidates:
                    chosen = {
                        **min(candidates, key=lambda f: f["period_start"]),
                        "accession_number": filing["accession_number"],
                    }
                    samples.append(
                        {
                            k: chosen[k]
                            for k in (
                                "fact_id",
                                "metric_id",
                                "source_tag",
                                "period_start",
                                "period_end",
                                "value",
                                "unit",
                                "accession_number",
                                "available_at",
                                "raw_id",
                            )
                        }
                    )
        coverage.append(entry)
    result = {
        "cik": cik,
        "label": issuer["label"],
        "name": issuer["name"],
        "last_collection": latest[0] if latest else None,
        "fact_count": len(facts),
        "source_hashes": evidence.hashes,
        "source_failures": dict(failures),
        "boundary_checks": boundaries,
        "fiscal_year_conflicts": fiscal_conflicts,
        "fiscal_year_reviews": reviewed_years,
        "coverage": coverage,
        "review_anchors": samples,
        "missing_annual_years": [r["year"] for r in coverage if not r["annual_filing"]],
        "latest_quarantine_reasons": dict(
            Counter(
                r.get("reason", "unknown") for q in quarantine for r in q["reasons"]
            )
        ),
    }
    # Explicit identity windows preserve the predecessor CIK in every fact and
    # annual coverage entry. Never relabel predecessor facts as the successor.
    result["predecessors"] = []
    for predecessor in issuer.get("predecessors", []):
        requested = [
            y
            for y in years
            if predecessor["start_year"] <= y <= predecessor["end_year"]
        ]
        if not requested:
            continue
        prior = audit_issuer(
            store,
            {
                **predecessor,
                "label": issuer["label"] + "-predecessor",
                "name": issuer["name"],
                "feature_model": issuer["feature_model"],
            },
            requested,
            archive_root,
        )
        result["predecessors"].append(prior)
        result["fact_count"] += prior["fact_count"]
        result["boundary_checks"] += prior["boundary_checks"]
        result["source_hashes"].update(prior["source_hashes"])
        for reason, count in prior["source_failures"].items():
            result["source_failures"][reason] = (
                result["source_failures"].get(reason, 0) + count
            )
        for entry in prior["coverage"]:
            entry["source_cik"] = predecessor["cik"]
            entry["identity_evidence"] = predecessor["evidence_url"]
            result["coverage"] = [
                entry if r["year"] == entry["year"] else r for r in result["coverage"]
            ]
    result["missing_annual_years"] = [
        r["year"] for r in result["coverage"] if not r["annual_filing"]
    ]
    for entry in result["coverage"]:
        entry.setdefault("source_cik", cik)
        if not entry["annual_filing"] and entry["year"] in issuer.get(
            "not_applicable_years", []
        ):
            entry["coverage_status"] = "not_applicable_after_acquisition"
            entry["coverage_evidence"] = issuer["reporting_end_evidence"]
    result["missing_expected_annual_years"] = [
        r["year"]
        for r in result["coverage"]
        if not r["annual_filing"]
        and r.get("coverage_status") != "not_applicable_after_acquisition"
    ]
    return result


def audit_cohort(store, cohort, archive_root, *, progress=lambda result: None):
    code_hash = source_tree_hash()
    results = []
    for issuer in cohort["issuers"]:
        try:
            with consistent_read(store) as view:
                result = audit_issuer(
                    view,
                    issuer,
                    range(cohort["start_year"], cohort["end_year"] + 1),
                    archive_root,
                )
        except (OSError, ValueError, KeyError, TypeError, ArithmeticError) as exc:
            result = {
                "cik": issuer["cik"],
                "label": issuer["label"],
                "audit_error": type(exc).__name__,
            }
        results.append(result)
        progress(result)
    failures = sum(
        bool(
            r.get("audit_error")
            or r.get("source_failures")
            or not r.get("fact_count")
            or (r.get("last_collection") or {}).get("status") != "success"
        )
        for r in results
    )
    payload = {
        "validation_version": "sec_source_validation_v1",
        "mapping_version": MAPPING_VERSION,
        "source_tree_hash": code_hash,
        "cohort": cohort,
        "golden_certification": "independent_accounting_review_pending",
        "issuer_count": len(results),
        "technical_failure_issuers": failures,
        "complete_annual_coverage_issuers": sum(
            not r.get("missing_annual_years", [None]) for r in results
        ),
        "complete_expected_annual_coverage_issuers": sum(
            not r.get("missing_expected_annual_years", [None]) for r in results
        ),
        "fact_rows": sum(r.get("fact_count", 0) for r in results),
        "boundary_checks": sum(r.get("boundary_checks", 0) for r in results),
        "issuers": results,
    }
    payload = normalize(payload)
    return {
        **payload,
        "report_hash": content_hash(payload),
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
