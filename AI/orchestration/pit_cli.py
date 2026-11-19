"""PIT commands kept separate from the legacy daily-market CLI."""

import argparse
import os
import signal
import threading
from datetime import date
from pathlib import Path

from AI.orchestration.archive import RawArchive
from AI.orchestration.features import financial_features
from AI.orchestration.pit_store import AsOfDataset, PitStore
from AI.orchestration.reference import import_reference
from AI.orchestration.sec import SecHttp, cik_string, collect_sec, utc

COMMANDS = {
    "reference-import",
    "sec-register",
    "sec-collect",
    "sec-schedule",
    "fundamentals",
    "features",
    "universe",
    "resolve",
    "bars",
}


def add_commands(sub):
    reference = sub.add_parser(
        "reference-import",
        help="Import explicitly sourced security history/actions/raw bars",
    )
    reference.add_argument("file", type=Path)
    register = sub.add_parser(
        "sec-register",
        help="Register an issuer for SEC collection; no ticker history inference",
    )
    register.add_argument("--cik", required=True, type=cik_string)
    for name in ["sec-collect", "sec-schedule"]:
        cmd = sub.add_parser(name)
        cmd.add_argument("--ciks", nargs="+", type=cik_string)
        cmd.add_argument("--delay-seconds", type=int, default=60)
        cmd.add_argument("--period-end-start", type=date.fromisoformat)
        cmd.add_argument("--period-end-end", type=date.fromisoformat)
        cmd.add_argument(
            "--archive", default=os.environ.get("RAW_ARCHIVE_PATH", "AI/data/bronze")
        )
        if name == "sec-schedule":
            cmd.add_argument("--interval", type=int, default=86400)
            cmd.add_argument("--scheduler-id", default="sec-companyfacts-v2")
    for name in ["fundamentals", "features"]:
        cmd = sub.add_parser(name)
        cmd.add_argument("--cik", required=True, type=cik_string)
        cmd.add_argument("--as-of", required=True, type=utc)
        if name == "fundamentals":
            cmd.add_argument("--metrics", nargs="+")
        else:
            cmd.add_argument("--industry-model", required=True, choices=["industrial"])
    universe = sub.add_parser("universe")
    universe.add_argument("--universe-id", required=True)
    universe.add_argument("--on", type=date.fromisoformat, required=True)
    universe.add_argument("--as-of", type=utc, required=True)
    resolve = sub.add_parser("resolve")
    resolve.add_argument("--type", choices=["ticker", "cik", "isin"], required=True)
    resolve.add_argument("--value", required=True)
    resolve.add_argument("--venue")
    resolve.add_argument("--on", type=date.fromisoformat, required=True)
    resolve.add_argument("--as-of", type=utc, required=True)
    bars = sub.add_parser("bars")
    bars.add_argument("--instrument-id", required=True)
    bars.add_argument("--start", type=date.fromisoformat, required=True)
    bars.add_argument("--end", type=date.fromisoformat, required=True)
    bars.add_argument(
        "--basis", choices=["raw", "split_adjusted", "total_return"], required=True
    )
    bars.add_argument("--as-of", type=utc, required=True)
    reference.add_argument(
        "--archive", default=os.environ.get("RAW_ARCHIVE_PATH", "AI/data/bronze")
    )


def execute(args, emit, code_sha):
    store = PitStore(args.db)
    api = AsOfDataset(store)
    if args.command == "reference-import":
        result = import_reference(
            store, RawArchive(args.archive), args.file.read_bytes(), code_sha()
        )
        emit(result)
        return int(result["status"] != "success")
    if args.command == "sec-register":
        store.register_issuer(args.cik)
        emit({"registered_cik": args.cik})
        return 0
    if args.command in {"sec-collect", "sec-schedule"}:
        # Load only the local environment, using the existing connection configuration.
        from dotenv import load_dotenv

        load_dotenv(Path(__file__).parents[2] / ".env")
        client, archive = SecHttp(), RawArchive(args.archive)

        def cycle():
            ciks = args.ciks or [
                r["cik"]
                for r in store.query(
                    "SELECT cik FROM ingestion.sec_issuer WHERE active ORDER BY cik"
                )
            ]
            if not ciks:
                raise ValueError("Register SEC issuers or specify --ciks")
            results = [
                collect_sec(
                    store,
                    archive,
                    client,
                    cik,
                    code_sha(),
                    args.delay_seconds,
                    period_end_start=args.period_end_start,
                    period_end_end=args.period_end_end,
                )
                for cik in ciks
            ]
            emit(results)
            return int(any(r["status"] != "success" for r in results))

        if args.command == "sec-collect":
            return cycle()
        if args.interval < 900:
            raise ValueError("Companyfacts scheduler interval must be >=900 seconds")
        stop = threading.Event()
        for sig in (signal.SIGINT, signal.SIGTERM):
            signal.signal(sig, lambda *_: stop.set())

        def pulse():
            while not stop.is_set():
                try:
                    store.pulse(args.scheduler_id)
                except Exception:
                    # Absence of heartbeat becomes unhealthy; no connection details logged.
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
    if args.command in {"fundamentals", "features"}:
        facts = api.fundamentals(
            args.cik, as_of=args.as_of, metrics=getattr(args, "metrics", None)
        )
        result = (
            facts
            if args.command == "fundamentals"
            else financial_features(
                facts, as_of=args.as_of, industry_model=args.industry_model
            )
        )
    elif args.command == "universe":
        result = api.universe(args.universe_id, on=args.on, as_of=args.as_of)
    elif args.command == "resolve":
        result = api.resolve(
            args.type, args.value, on=args.on, as_of=args.as_of, venue=args.venue
        )
    elif args.command == "bars":
        result = api.bars(
            args.instrument_id,
            start=args.start,
            end=args.end,
            basis=args.basis,
            as_of=args.as_of,
        )
    else:
        raise argparse.ArgumentError(None, "Unknown PIT command")
    emit(result)
    return 0
