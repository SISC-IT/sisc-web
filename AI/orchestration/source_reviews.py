"""Narrow, versioned corrections backed by reviewed primary filing evidence.

Raw facts are never changed. A review applies only to its exact issuer, period
and observed values; it cannot silently bless a future revision.
"""

import hashlib
import json
from decimal import Decimal
from pathlib import Path

REVIEWS = json.loads(Path(__file__).with_name("sec_source_reviews.json").read_text())


def verify_evidence(review, archive_root):
    root = Path(archive_root).resolve()
    evidence = review["evidence"]
    path = (root / evidence["archive_path"]).resolve()
    if not path.is_relative_to(root):
        raise ValueError("Review evidence outside archive")
    if hashlib.sha256(path.read_bytes()).hexdigest() != evidence["sha256"]:
        raise ValueError("Review evidence checksum mismatch")


def debt_review(facts, period):
    for review in REVIEWS["debt"]:
        if str(period) != review["period_end"]:
            continue
        selected = {}
        for metric in review["expected"]:
            candidates = [
                f
                for f in facts
                if f.get("cik") == review["cik"]
                and f["metric_id"] == metric
                and f["period_end"] == period
            ]
            if not candidates:
                break
            selected[metric] = max(
                candidates, key=lambda f: (f["available_at"], -f.get("tag_priority", 0))
            )
        if len(selected) != len(review["expected"]):
            continue
        if any(not f.get("filing_id") for f in selected.values()):
            continue
        # Value-producing balance-sheet components must come from one filing.
        # An exact, reviewed principal disclosure may be from an older filing;
        # it is comparison evidence only, never included in the carrying value.
        value_metrics = review.get("weights", review["expected"])
        if len({str(selected[m]["filing_id"]) for m in value_metrics}) != 1:
            continue
        if all(
            Decimal(selected[m]["value"]) == Decimal(v)
            for m, v in review["expected"].items()
        ):
            return review
    return None


def fiscal_year_review(filing, source_years, archive_root):
    for review in REVIEWS["fiscal_year"]:
        if (
            filing["cik"] == review["cik"]
            and filing["accession_number"] == review["accession"]
            and str(filing["period_end"]) == review["period_end"]
            and source_years == {review["source_fy"]}
        ):
            verify_evidence(review, archive_root)
            return review
    return None
