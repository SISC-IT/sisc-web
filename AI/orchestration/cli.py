"""python -m AI.orchestration.cli --help"""

import argparse
import json
import logging
import os
import signal
import subprocess
import threading
from datetime import date, datetime, timezone
from pathlib import Path

from AI.orchestration.archive import RawArchive
from AI.orchestration.core import completed_sessions, plan_ranges
from AI.orchestration.market import YahooDaily
from AI.orchestration.runner import run_all
from AI.orchestration.store import Store, migrate


def code_sha():
    if os.environ.get("CODE_SHA"):
        return os.environ["CODE_SHA"]
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=Path(__file__).parents[2],
            stderr=subprocess.DEVNULL,
            text=True,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def emit(value):
    print(json.dumps(value, default=str, ensure_ascii=False, indent=2), flush=True)


def gap_report(store, partitions, start=None, end=None):
    result = []
    now = datetime.now(timezone.utc)
    for p in partitions:
        lower = max(p["start_date"], start or p["start_date"])
        upper = min(p.get("end_date") or now.date(), end or now.date(), now.date())
        expected = completed_sessions(p["calendar"], lower, upper, now)
        observed = store.observed(p["dataset_id"], p["partition_key"])
        legacy = store.legacy_observed(
            p["dataset_id"], p["partition_key"], lower, upper
        )
        gaps = plan_ranges(expected, observed)
        result.append(
            {
                "dataset": p["dataset_id"],
                "partition": p["partition_key"],
                "expected": len(expected),
                "present": len(set(expected) & observed),
                "missing": len(set(expected) - observed),
                "legacy_missing": len(set(expected) - legacy),
                "legacy_gaps": [vars(g) for g in plan_ranges(expected, legacy)],
                "latest_expected": expected[-1] if expected else None,
                "latest_observed": max(observed) if observed else None,
                "gaps": [vars(g) for g in gaps],
            }
        )
    return result


def legacy_report(store):
    # Identifiers are a fixed allowlist, never user input. Missing tables remain visible.
    tables = {
        "stock_info": "updated_at",
        "price_data": "date",
        "crypto_price_data": "date",
        "macroeconomic_indicators": "date",
        "company_fundamentals": "date",
        "event_calendar": "event_date",
        "sector_returns": "date",
        "market_breadth": "date",
        "news_sentiment": "date",
    }
    reports = []
    for table, column in tables.items():
        exists = store.query("SELECT to_regclass(%s) AS name", ("public." + table,))[0][
            "name"
        ]
        if not exists:
            reports.append({"table": table, "status": "missing"})
            continue
        try:
            stats = store.query(
                f'SELECT count(*) AS rows, min("{column}") AS oldest, '
                f'max("{column}") AS latest FROM public."{table}"'
            )[0]
            reports.append({"table": table, **stats})
        except Exception as exc:
            reports.append(
                {"table": table, "status": "failed", "error_class": type(exc).__name__}
            )
    return reports


def parser():
    result = argparse.ArgumentParser(
        description="V2 dataset operations (inclusive date ranges)"
    )
    result.add_argument("--db", default="db")
    sub = result.add_subparsers(dest="command", required=True)
    sub.add_parser("migrate", help="Apply additive metadata migration explicitly")
    sub.add_parser(
        "baseline", help="Read-only legacy row counts/latest dates; no secrets"
    )
    register = sub.add_parser(
        "register",
        help="Register a verified listing range; do not infer historical universe",
    )
    register.add_argument(
        "--dataset", required=True, choices=["price_us_daily", "crypto_daily"]
    )
    register.add_argument("--partition", required=True)
    register.add_argument("--calendar", required=True, choices=["XNYS", "XNAS", "24/7"])
    register.add_argument("--start", type=date.fromisoformat, required=True)
    register.add_argument("--end", type=date.fromisoformat)
    for name in ["gaps", "backfill", "run", "schedule"]:
        command = sub.add_parser(name)
        command.add_argument("--dataset", choices=["price_us_daily", "crypto_daily"])
        command.add_argument("--partition")
        command.add_argument("--start", type=date.fromisoformat)
        command.add_argument("--end", type=date.fromisoformat)
        if name != "gaps":
            command.add_argument(
                "--archive",
                default=os.environ.get("RAW_ARCHIVE_PATH", "AI/data/bronze"),
            )
        if name == "schedule":
            command.add_argument(
                "--interval",
                type=int,
                default=21600,
                help="Seconds between catch-up cycles",
            )
            command.add_argument("--scheduler-id", default="daily-market-v2")
    status = sub.add_parser("status")
    status.add_argument("--limit", type=int, default=30)
    health = sub.add_parser("health")
    health.add_argument("--scheduler-id", default="daily-market-v2")
    from AI.orchestration.pit_cli import add_commands

    add_commands(sub)
    from AI.orchestration.macro_cli import add_commands as add_macro_commands

    add_macro_commands(sub)
    from AI.orchestration.quality_cli import add_commands as add_quality_commands

    add_quality_commands(sub)
    from AI.orchestration.validation_cli import add_commands as add_validation_commands

    add_validation_commands(sub)
    return result


def main(argv=None):
    args = parser().parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    if (
        getattr(args, "start", None)
        and getattr(args, "end", None)
        and args.start > args.end
    ):
        raise ValueError("start must not be after end")
    if args.command == "migrate":
        version = migrate(args.db)
        emit({"schema_version": version})
        return 0
    from AI.orchestration.pit_cli import COMMANDS, execute

    if args.command in COMMANDS:
        return execute(args, emit, code_sha)
    from AI.orchestration.macro_cli import COMMANDS as MACRO_COMMANDS
    from AI.orchestration.macro_cli import execute as execute_macro

    if args.command in MACRO_COMMANDS:
        return execute_macro(args, emit, code_sha)
    from AI.orchestration.quality_cli import COMMANDS as QUALITY_COMMANDS
    from AI.orchestration.quality_cli import execute as execute_quality

    if args.command in QUALITY_COMMANDS:
        return execute_quality(args, emit, code_sha)
    from AI.orchestration.validation_cli import COMMANDS as VALIDATION_COMMANDS
    from AI.orchestration.validation_cli import execute as execute_validation

    if args.command in VALIDATION_COMMANDS:
        return execute_validation(args, emit, code_sha)
    store = Store(args.db)
    if args.command == "baseline":
        emit(
            {
                "code_sha": code_sha(),
                "tables": legacy_report(store),
                "execution_owner": "requires_server_inspection",
            }
        )
        return 0
    if args.command == "register":
        with store.lock(args.dataset, args.partition) as acquired:
            if not acquired:
                raise RuntimeError("Partition is currently being collected")
            store.register(
                args.dataset, args.partition, args.calendar, args.start, args.end
            )
        emit({"registered": args.partition, "dataset": args.dataset})
        return 0
    if args.command == "status":
        emit(
            {
                "runs": store.query(
                    "SELECT * FROM ingestion.dataset_run ORDER BY started_at DESC LIMIT %s",
                    (max(1, min(args.limit, 1000)),),
                ),
                "watermarks": store.query("SELECT * FROM ingestion.dataset_watermark"),
                "scheduler": store.query("SELECT * FROM ingestion.scheduler_heartbeat"),
                "quarantine_count": store.query(
                    "SELECT count(*) AS count FROM ingestion.quarantine"
                )[0]["count"],
            }
        )
        return 0
    if args.command == "health":
        rows = store.query(
            """SELECT heartbeat_at > now() - interval '2 minutes' AS healthy
            FROM ingestion.scheduler_heartbeat WHERE scheduler_id=%s""",
            (args.scheduler_id,),
        )
        return 0 if rows and rows[0]["healthy"] else 1
    partitions = store.partitions(args.dataset, args.partition)
    if not partitions:
        raise RuntimeError(
            "No registered partitions; register explicit listing ranges first"
        )
    if args.command == "gaps":
        report = gap_report(store, partitions, args.start, args.end)
        emit(report)
        return 1 if any(r["missing"] or r["legacy_missing"] for r in report) else 0
    archive, connector = RawArchive(args.archive), YahooDaily()
    if args.command != "schedule":
        results = run_all(
            store,
            archive,
            connector,
            partitions,
            code_sha(),
            start=args.start,
            end=args.end,
            refresh=args.command == "run",
        )
        emit(results)
        return 1 if any(r["status"] in {"failed", "locked"} for r in results) else 0
    if args.interval < 60:
        raise ValueError("scheduler interval must be at least 60 seconds")
    stopped = threading.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: stopped.set())

    def pulse():
        while not stopped.is_set():
            try:
                store.pulse(args.scheduler_id)
            except Exception as exc:
                logging.error("Scheduler heartbeat failed (%s)", type(exc).__name__)
            stopped.wait(30)

    # One scheduler owner across containers; individual partitions also have locks.
    with store.lock("scheduler", args.scheduler_id) as acquired:
        if not acquired:
            raise RuntimeError("Scheduler already running")
        worker = threading.Thread(target=pulse, daemon=True)
        worker.start()
        try:
            while not stopped.is_set():
                try:
                    partitions = store.partitions(args.dataset, args.partition)
                    results = run_all(
                        store,
                        archive,
                        connector,
                        partitions,
                        code_sha(),
                        start=args.start,
                        end=args.end,
                        refresh=True,
                    )
                    emit(results)
                except Exception as exc:
                    logging.error("Scheduler cycle failed (%s)", type(exc).__name__)
                stopped.wait(args.interval)
        finally:
            stopped.set()
            worker.join(timeout=5)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        # Do not print connection DSNs, provider URLs or credentials.
        logging.error("Command failed (%s)", type(exc).__name__)
        raise SystemExit(1)
