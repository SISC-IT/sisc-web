"""Independent legacy dataset jobs. Completion is unverified until adapters migrate.

Most legacy collectors swallow provider/DB errors internally. A return without an
exception must NOT advance a watermark or claim validated data success.
"""

import logging
from contextlib import nullcontext
from dataclasses import dataclass
from importlib import import_module


@dataclass(frozen=True)
class LegacyJob:
    name: str
    skip: str
    module: str
    collector: str


JOBS = (
    LegacyJob("macro", "skip_macro", "macro_data", "MacroDataCollector"),
    LegacyJob("stock_info", "skip_info", "stock_info_collector", "StockInfoCollector"),
    LegacyJob(
        "fundamentals",
        "skip_fund",
        "company_fundamentals_data",
        "FundamentalsDataCollector",
    ),
    LegacyJob("price_us", "skip_price", "market_data", "MarketDataCollector"),
    LegacyJob("crypto", "skip_crypto", "crypto_data", "CryptoDataCollector"),
    LegacyJob("events", "skip_event", "event_data", "EventDataCollector"),
    LegacyJob(
        "sector", "skip_breadth", "market_breadth_data", "MarketBreadthCollector"
    ),
    LegacyJob(
        "breadth_stats",
        "skip_stats",
        "market_breadth_stats",
        "MarketBreadthStatsCollector",
    ),
)


def invoke(job, args, tickers):
    cls = getattr(
        import_module("AI.modules.data_collector.components." + job.module),
        job.collector,
    )
    collector = cls(db_name=args.db)
    if job.name == "macro":
        collector.run(lookback_days=365 * (10 if args.repair else 2))
    elif job.name in {"stock_info", "fundamentals"}:
        collector.update_tickers(tickers)
    elif job.name in {"price_us", "crypto"}:
        collector.update_tickers(
            ["BTC-USD", "ETH-USD"] if job.name == "crypto" else tickers,
            repair_mode=args.repair,
        )
    elif job.name == "events":
        collector.update_macro_events(force_update=args.repair)
        if tickers:
            collector.update_earnings_dates(tickers)
    else:
        collector.run(repair_mode=args.repair)


def execute_jobs(args, tickers, store=None, code_sha="unknown", invoke_job=invoke):
    results = []
    for job in JOBS:
        if getattr(args, job.skip) or (
            job.name in {"stock_info", "fundamentals", "price_us"} and not tickers
        ):
            continue
        run_id = None
        try:
            ownership = (
                store.lock("legacy_" + job.name, "all") if store else nullcontext(True)
            )
            with ownership as acquired:
                if not acquired:
                    results.append({"dataset": job.name, "status": "locked"})
                    continue
                if store:
                    run_id = store.start(
                        "legacy_" + job.name,
                        "all",
                        None,
                        code_sha,
                        {
                            "repair": args.repair,
                            "tickers": tickers,
                            "verification": "legacy_unverified",
                        },
                    )
                invoke_job(job, args, tickers)
                if store:
                    store.finish(run_id, "unverified")
                results.append({"dataset": job.name, "status": "unverified"})
        except Exception as exc:
            if store and run_id:
                store.finish(run_id, "failed", type(exc).__name__)
            logging.error("Legacy dataset %s failed (%s)", job.name, type(exc).__name__)
            results.append({"dataset": job.name, "status": "failed"})
    return results
