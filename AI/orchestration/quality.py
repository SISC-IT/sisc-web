"""Persisted quality checks and deduplicated local incident transitions."""

import json
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal

from AI.orchestration.consistent import consistent_read
from AI.orchestration.core import completed_sessions

POLICY_VERSION = "dataset_quality_v1"


def evaluate(store, now):
    policies = {
        p["dataset_id"]: p for p in store.query("SELECT * FROM ingestion.dq_policy")
    }
    runs = store.query("SELECT * FROM ingestion.dataset_run ORDER BY started_at,run_id")
    partitions = store.partitions()
    issuers = store.query("SELECT cik FROM ingestion.sec_issuer WHERE active")
    series = store.series_catalog()
    scopes = {(r["dataset_id"], r["partition_key"]) for r in runs}
    scopes.update((p["dataset_id"], p["partition_key"]) for p in partitions)
    scopes.update(("sec_companyfacts", i["cik"]) for i in issuers)
    scopes.update(("fred_vintage", s["series_id"]) for s in series)
    results = []

    def check(dataset, partition, rule, passed, details, severity="critical"):
        results.append(
            dict(
                dataset_id=dataset,
                partition_key=partition,
                rule=rule,
                passed=bool(passed),
                severity=severity,
                details=details,
            )
        )

    check(
        "platform",
        "all",
        "registered_or_observed_dataset",
        scopes,
        {"scope_count": len(scopes)},
    )
    for dataset, partition in sorted(scopes):
        policy = policies.get(dataset, policies["*"])
        history = [
            r
            for r in runs
            if (r["dataset_id"], r["partition_key"]) == (dataset, partition)
        ]
        finished = [r for r in history if r["status"] not in {"running", "skipped"}]
        latest = finished[-1] if finished else None
        good = [
            r for r in finished if r["status"] == "success" and r["fetched_count"] > 0
        ]
        latest_good = max(
            (r["finished_at"] for r in good if r["finished_at"]), default=None
        )
        hours = next(
            (
                s["freshness_hours"]
                for s in series
                if dataset == "fred_vintage" and s["series_id"] == partition
            ),
            policy["freshness_hours"],
        )
        check(
            dataset,
            partition,
            "collection_freshness",
            latest_good is not None and now - latest_good <= timedelta(hours=hours),
            {
                "last_verified_success": str(latest_good) if latest_good else None,
                "max_hours": hours,
            },
        )
        check(
            dataset,
            partition,
            "last_completed_run",
            latest is not None and latest["status"] == "success",
            {"status": latest["status"] if latest else "never_run"},
        )
        check(
            dataset,
            partition,
            "nonempty_success",
            latest is None
            or latest["status"] != "success"
            or latest["fetched_count"] > 0,
            {
                "run_id": str(latest["run_id"]) if latest else None,
                "fetched_count": latest["fetched_count"] if latest else None,
            },
        )
        stalled = [
            str(r["run_id"])
            for r in history
            if r["status"] == "running"
            and now - r["heartbeat_at"] > timedelta(minutes=policy["heartbeat_minutes"])
        ]
        check(
            dataset,
            partition,
            "worker_heartbeat",
            not stalled,
            {"stalled_run_ids": stalled, "max_minutes": policy["heartbeat_minutes"]},
        )
        recent = [r for r in finished if r["started_at"] >= now - timedelta(days=7)]
        successful = sum(
            r["status"] == "success" and r["fetched_count"] > 0 for r in recent
        )
        ratio = Decimal(successful) / len(recent) if recent else Decimal(0)
        check(
            dataset,
            partition,
            "success_ratio_7d",
            recent and ratio >= policy["success_ratio"],
            {
                "runs": len(recent),
                "verified_successes": successful,
                "ratio": str(ratio),
                "target": str(policy["success_ratio"]),
            },
            "warning",
        )
    for p in partitions:
        dataset, partition = p["dataset_id"], p["partition_key"]
        expected = set(
            completed_sessions(
                p["calendar"],
                p["start_date"],
                min(p["end_date"] or now.date(), now.date()),
                now,
            )
        )
        observed = store.observed(dataset, partition)
        legacy = store.legacy_observed(dataset, partition, p["start_date"], now.date())
        missing = sorted(expected - (observed & legacy))
        ratio = (
            Decimal(len(expected) - len(missing)) / len(expected)
            if expected
            else Decimal(1)
        )
        target = policies.get(dataset, policies["*"])["completeness_ratio"]
        check(
            dataset,
            partition,
            "session_completeness",
            ratio >= target,
            {
                "expected": len(expected),
                "missing": len(missing),
                "ratio": str(ratio),
                "target": str(target),
                "first_missing": [str(d) for d in missing[:20]],
            },
        )
    for s in store.series_status():
        check(
            "fred_vintage",
            s["series_id"],
            "registered_range_scanned",
            s["last_verified_scan"] is not None,
            {
                "last_verified_scan": str(s["last_verified_scan"])
                if s["last_verified_scan"]
                else None,
                "source_first_observation": str(s["source_first_observation"]),
                "source_last_observation": str(s["source_last_observation"]),
            },
        )
    violations = store.query("""SELECT
        (SELECT count(*) FROM ingestion.fundamental_fact WHERE available_at<published_at) +
        (SELECT count(*) FROM ingestion.filing WHERE available_at<accepted_at) +
        (SELECT count(*) FROM ingestion.reference_record WHERE available_at<published_at) +
        (SELECT count(*) FROM ingestion.series_observation WHERE available_at<=vintage_at
          OR vintage_date<observation_date OR (available_to IS NOT NULL AND available_to<=available_at)) AS n""")[
        0
    ]["n"]
    check(
        "platform",
        "all",
        "temporal_integrity",
        violations == 0,
        {"violations": violations},
    )
    return results, policies


def run_quality(store, code_sha, *, now=None):
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        raise ValueError("Timezone-aware check time required")
    with store.lock("quality", "all") as acquired:
        if not acquired:
            raise RuntimeError("Quality runner already active")
        with consistent_read(store) as view:
            results, policies = evaluate(view, now)
        check_id, events = str(uuid.uuid4()), []
        with store.transaction() as cur:
            cur.execute(
                "INSERT INTO ingestion.dq_run VALUES (%s,%s,%s,%s,%s::jsonb,%s,%s)",
                (
                    check_id,
                    now,
                    code_sha,
                    POLICY_VERSION,
                    json.dumps(policies, default=str),
                    len(results),
                    sum(not r["passed"] for r in results),
                ),
            )
            for r in results:
                key = (r["dataset_id"], r["partition_key"], r["rule"])
                cur.execute(
                    "INSERT INTO ingestion.dq_result VALUES (%s,%s,%s,%s,%s,%s,%s::jsonb)",
                    (
                        check_id,
                        *key,
                        r["severity"],
                        r["passed"],
                        json.dumps(r["details"]),
                    ),
                )
                cur.execute(
                    "SELECT active FROM ingestion.dq_incident WHERE dataset_id=%s AND partition_key=%s AND rule=%s",
                    key,
                )
                previous = cur.fetchone()
                active = not r["passed"]
                if (previous is None and active) or (
                    previous is not None and previous[0] != active
                ):
                    event = "opened" if active else "resolved"
                    cur.execute(
                        """INSERT INTO ingestion.dq_alert(check_id,dataset_id,partition_key,rule,event)
                                VALUES (%s,%s,%s,%s,%s) RETURNING alert_id""",
                        (check_id, *key, event),
                    )
                    events.append(
                        dict(
                            alert_id=cur.fetchone()[0],
                            event=event,
                            dataset_id=key[0],
                            partition_key=key[1],
                            rule=key[2],
                        )
                    )
                cur.execute(
                    """INSERT INTO ingestion.dq_incident VALUES (%s,%s,%s,%s,%s)
                    ON CONFLICT(dataset_id,partition_key,rule) DO UPDATE SET
                    active=excluded.active,last_check_id=excluded.last_check_id""",
                    (*key, active, check_id),
                )
        return {
            "check_id": check_id,
            "failures": [r for r in results if not r["passed"]],
            "checks": len(results),
            "alerts": events,
        }
