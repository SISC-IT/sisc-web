"""Quality operations and offline snapshot commands."""

import json
import signal
import threading
from pathlib import Path

from AI.orchestration.quality import run_quality
from AI.orchestration.series_store import SeriesStore
from AI.orchestration.snapshot import (
    baseline,
    export_snapshot,
    register_snapshot,
    verify_snapshot,
)

COMMANDS = {
    "dq-run",
    "dq-schedule",
    "dq-alerts",
    "dq-policy",
    "snapshot-export",
    "snapshot-verify",
    "snapshot-register",
    "snapshot-baseline",
}


def add_commands(sub):
    sub.add_parser("dq-run")
    schedule = sub.add_parser("dq-schedule")
    schedule.add_argument("--interval", type=int, default=900)
    schedule.add_argument("--scheduler-id", default="quality-v4")
    alerts = sub.add_parser("dq-alerts")
    alerts.add_argument("--after-id", type=int, default=0)
    alerts.add_argument("--limit", type=int, default=100)
    policy = sub.add_parser("dq-policy")
    policy.add_argument("--dataset", required=True)
    policy.add_argument("--freshness-hours", type=int, required=True)
    policy.add_argument("--heartbeat-minutes", type=int, default=10)
    policy.add_argument("--success-ratio", type=float, default=0.99)
    policy.add_argument("--completeness-ratio", type=float, default=0.995)
    export = sub.add_parser("snapshot-export")
    export.add_argument("--spec", type=Path, required=True)
    export.add_argument("--output", type=Path, required=True)
    export.add_argument("--id", required=True)
    for name in ("snapshot-verify", "snapshot-register", "snapshot-baseline"):
        cmd = sub.add_parser(name)
        cmd.add_argument("directory", type=Path)
        if name != "snapshot-register":
            cmd.add_argument("--expected-hash")
        if name == "snapshot-baseline":
            cmd.add_argument("--query", required=True)


def execute(args, emit, code_sha):
    if args.command == "snapshot-verify":
        manifest, _ = verify_snapshot(args.directory, expected_hash=args.expected_hash)
        emit(
            {
                "verified": True,
                "content_hash": manifest["content_hash"],
                "row_counts": manifest["row_counts"],
            }
        )
        return 0
    if args.command == "snapshot-baseline":
        emit(baseline(args.directory, args.query, expected_hash=args.expected_hash))
        return 0
    store = SeriesStore(args.db)
    if args.command == "snapshot-register":
        emit({"registered": register_snapshot(store, args.directory)})
        return 0
    if args.command == "snapshot-export":
        emit(
            export_snapshot(
                store,
                json.loads(args.spec.read_text(encoding="utf-8-sig")),
                args.output,
                args.id,
                code_sha(),
            )
        )
        return 0
    if args.command == "dq-alerts":
        if args.after_id < 0 or not 1 <= args.limit <= 1000:
            raise ValueError("Invalid alert page")
        emit(
            store.query(
                """SELECT a.*,r.severity,r.details FROM ingestion.dq_alert a
            JOIN ingestion.dq_result r USING(check_id,dataset_id,partition_key,rule)
            WHERE alert_id>%s ORDER BY alert_id LIMIT %s""",
                (args.after_id, args.limit),
            )
        )
        return 0
    if args.command == "dq-policy":
        with store.transaction() as cur:
            cur.execute(
                """INSERT INTO ingestion.dq_policy VALUES (%s,%s,%s,%s,%s)
                ON CONFLICT(dataset_id) DO UPDATE SET freshness_hours=excluded.freshness_hours,
                heartbeat_minutes=excluded.heartbeat_minutes,success_ratio=excluded.success_ratio,
                completeness_ratio=excluded.completeness_ratio""",
                (
                    args.dataset,
                    args.freshness_hours,
                    args.heartbeat_minutes,
                    args.success_ratio,
                    args.completeness_ratio,
                ),
            )
            if args.dataset == "fred_vintage":
                cur.execute(
                    "UPDATE ingestion.series_catalog SET freshness_hours=%s",
                    (args.freshness_hours,),
                )
        emit({"updated": args.dataset})
        return 0
    if args.command == "dq-run":
        result = run_quality(store, code_sha())
        emit(result)
        return int(bool(result["failures"]))
    if args.interval < 60:
        raise ValueError("Quality interval must be at least 60 seconds")
    stop = threading.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: stop.set())

    def pulse():
        while not stop.is_set():
            try:
                store.pulse(args.scheduler_id)
            except Exception:
                pass
            stop.wait(30)

    with store.lock("scheduler", args.scheduler_id) as acquired:
        if not acquired:
            raise RuntimeError("Quality scheduler already running")
        worker = threading.Thread(target=pulse, daemon=True)
        worker.start()
        try:
            while not stop.is_set():
                try:
                    result = run_quality(store, code_sha())
                    if result["alerts"]:
                        emit(
                            {"check_id": result["check_id"], "alerts": result["alerts"]}
                        )
                except Exception as exc:
                    emit({"status": "failed", "error_class": type(exc).__name__})
                stop.wait(args.interval)
        finally:
            stop.set()
            worker.join(timeout=5)
    return 0
