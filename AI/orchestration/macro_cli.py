"""Explicit catalog registration, bounded vintage backfill and required as-of reads."""

import os
import signal
import threading
from datetime import date
from pathlib import Path

from AI.orchestration.archive import RawArchive
from AI.orchestration.fred import FredHttp, collect_fred, series_id
from AI.orchestration.sec import utc
from AI.orchestration.series_catalog import DEFAULT_SERIES
from AI.orchestration.series_store import SeriesStore

COMMANDS = {
    "series-register",
    "fred-collect",
    "fred-schedule",
    "series",
    "series-status",
}


def add_commands(sub):
    register = sub.add_parser(
        "series-register",
        help="Register FRED IDs; --defaults selects 25 curated series",
    )
    group = register.add_mutually_exclusive_group(required=True)
    group.add_argument("--ids", nargs="+", type=series_id)
    group.add_argument("--defaults", action="store_true")
    register.add_argument("--category", default="custom")
    register.add_argument("--start", required=True, type=date.fromisoformat)
    register.add_argument("--end", type=date.fromisoformat)
    for name in ["fred-collect", "fred-schedule"]:
        cmd = sub.add_parser(name)
        cmd.add_argument("--ids", nargs="+", type=series_id)
        cmd.add_argument(
            "--archive", default=os.environ.get("RAW_ARCHIVE_PATH", "AI/data/bronze")
        )
        cmd.add_argument("--page-limit", type=int, default=10000)
        cmd.add_argument("--max-pages", type=int, default=100)
        if name == "fred-schedule":
            cmd.add_argument("--interval", type=int, default=86400)
            cmd.add_argument("--scheduler-id", default="fred-vintage-v3")
    query = sub.add_parser("series")
    query.add_argument("--ids", nargs="+", type=series_id, required=True)
    query.add_argument("--start", type=date.fromisoformat, required=True)
    query.add_argument("--end", type=date.fromisoformat, required=True)
    query.add_argument("--as-of", type=utc, required=True)
    query.add_argument("--data-as-of", type=utc)
    sub.add_parser("series-status")


def execute(args, emit, code_sha):
    store = SeriesStore(args.db)
    if args.command == "series-register":
        ids = DEFAULT_SERIES if args.defaults else {i: args.category for i in args.ids}
        for identifier, category in ids.items():
            store.register_series(identifier, category, args.start, args.end)
        emit({"registered": list(ids)})
        return 0
    if args.command == "series":
        emit(
            store.series(
                args.ids,
                start=args.start,
                end=args.end,
                as_of=args.as_of,
                data_as_of=args.data_as_of,
            )
        )
        return 0
    if args.command == "series-status":
        result = store.series_status()
        emit(result)
        return int(
            not result
            or any(
                r["stale_collection"] or r["last_run_status"] != "success"
                for r in result
            )
        )
    from dotenv import load_dotenv

    load_dotenv(Path(__file__).parents[2] / ".env")
    client, archive = FredHttp(), RawArchive(args.archive)

    def cycle():
        catalog = store.series_catalog(args.ids)
        if not catalog:
            raise ValueError("Register series first")
        results = []
        for entry in catalog:
            try:
                results.append(
                    collect_fred(
                        store,
                        archive,
                        client,
                        entry,
                        code_sha(),
                        page_limit=args.page_limit,
                        max_pages=args.max_pages,
                    )
                )
            except Exception as exc:
                results.append(
                    {
                        "series_id": entry["series_id"],
                        "status": "failed",
                        "error_class": type(exc).__name__,
                    }
                )
        emit(results)
        return int(any(r["status"] != "success" for r in results))

    if args.command == "fred-collect":
        return cycle()
    if args.interval < 3600:
        raise ValueError("FRED full-vintage scan interval must be >=3600 seconds")
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
            return 1
        worker = threading.Thread(target=pulse, daemon=True)
        worker.start()
        try:
            while not stop.is_set():
                try:
                    cycle()
                except Exception as exc:
                    emit({"status": "failed", "error_class": type(exc).__name__})
                stop.wait(args.interval)
        finally:
            stop.set()
            worker.join(timeout=5)
    return 0
