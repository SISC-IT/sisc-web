import logging
import random
import time
from datetime import datetime, timezone
from decimal import Decimal

from AI.orchestration.core import completed_sessions, plan_ranges, validate_bars

LOG = logging.getLogger(__name__)


def run_partition(
    store,
    archive,
    connector,
    partition,
    code_sha,
    start=None,
    end=None,
    refresh=False,
    now=None,
    sleep=time.sleep,
):
    now = now or datetime.now(timezone.utc)
    dataset, ticker = partition["dataset_id"], partition["partition_key"]
    with store.lock(dataset, ticker) as acquired:
        if not acquired:
            return {"dataset": dataset, "partition": ticker, "status": "locked"}
        full_end = min(partition.get("end_date") or now.date(), now.date())
        all_expected = completed_sessions(
            partition["calendar"], partition["start_date"], full_end, now
        )
        expected = [
            d
            for d in all_expected
            if (start is None or d >= start) and (end is None or d <= end)
        ]
        observed = store.observed(dataset, ticker)
        # A missing compatibility row also needs recovery after an external delete/restore.
        observed &= store.legacy_observed(
            dataset, ticker, partition["start_date"], full_end
        )
        spans = plan_ranges(
            expected, observed, partition["overlap_sessions"] if refresh else 0
        )
        results = []
        for span in spans:
            config = {
                "calendar": partition["calendar"],
                "adapter": "yahoo_sdk_v1",
                "availability_policy": "ingestion_time_v1",
                "refresh": refresh,
            }
            run_id = store.start(dataset, ticker, span, code_sha, config)
            raw_id = None
            try:
                for attempt in range(1, 4):
                    store.heartbeat(run_id, attempt)
                    try:
                        payload = connector.fetch(ticker, span)
                        break
                    except Exception as exc:
                        # Retry only transient transport/rate failures. Never log messages/URLs.
                        retryable = isinstance(
                            exc, (TimeoutError, ConnectionError)
                        ) or type(exc).__name__ in {
                            "YFRateLimitError",
                            "Timeout",
                            "ReadTimeout",
                            "ConnectTimeout",
                            "ConnectionError",
                            "RequestsError",
                            "RequestException",
                        }
                        if not retryable or attempt == 3:
                            raise
                        sleep(min(30, 2**attempt + random.random()))
                fetched_at = datetime.now(timezone.utc)
                checksum, uri = archive.put(payload)
                raw_id = store.raw(
                    run_id,
                    checksum,
                    uri,
                    fetched_at,
                    {
                        "ticker": ticker,
                        "start": span.start.isoformat(),
                        "end": span.end.isoformat(),
                    },
                )
                try:
                    rows = connector.parse(payload)
                except (ValueError, TypeError, KeyError, IndexError):
                    store.quarantine(run_id, raw_id, ["invalid_schema"], 0)
                    results.append("quarantined")
                    continue
                reasons = validate_bars(
                    rows, [d for d in expected if span.start <= d <= span.end]
                )
                if (
                    not reasons
                    and dataset == "price_us_daily"
                    and any(
                        Decimal(r["volume"]) != Decimal(r["volume"]).to_integral_value()
                        for r in rows
                    )
                ):
                    reasons.append("fractional_stock_volume")
                if reasons:
                    store.quarantine(run_id, raw_id, reasons, len(rows))
                    results.append("quarantined")
                    LOG.warning(
                        "Dataset %s partition %s run %s quarantined: %s",
                        dataset,
                        ticker,
                        run_id,
                        reasons,
                    )
                    continue
                store.commit_bars(
                    dataset, ticker, rows, run_id, raw_id, fetched_at, all_expected
                )
                results.append("success")
            except Exception as exc:
                # Metadata intentionally excludes arbitrary provider exception strings (secrets).
                store.finish(run_id, "failed", type(exc).__name__)
                results.append("failed")
                LOG.error(
                    "Dataset %s partition %s run %s failed (%s)",
                    dataset,
                    ticker,
                    run_id,
                    type(exc).__name__,
                )
        return {
            "dataset": dataset,
            "partition": ticker,
            "status": "failed"
            if any(s != "success" for s in results)
            else ("success" if results else "noop"),
            "ranges": len(spans),
            "results": results,
        }


def run_all(store, archive, connector, partitions, code_sha, **kwargs):
    results = []
    for partition in partitions:
        try:
            results.append(
                run_partition(store, archive, connector, partition, code_sha, **kwargs)
            )
        except Exception as exc:
            LOG.error(
                "Partition planning failed: %s (%s)",
                partition["partition_key"],
                type(exc).__name__,
            )
            results.append(
                {
                    "dataset": partition["dataset_id"],
                    "partition": partition["partition_key"],
                    "status": "failed",
                    "error_class": type(exc).__name__,
                }
            )
    return results
