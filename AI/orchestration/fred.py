"""FRED/ALFRED real-time intervals, with conservative day-precision availability."""

import json
import os
import random
import re
import time
import uuid
from datetime import date, datetime, timedelta, timezone
from datetime import time as daytime
from decimal import Decimal
from email.utils import parsedate_to_datetime
from zoneinfo import ZoneInfo

from AI.orchestration.core import FetchRange, canonical_bytes, content_hash
from AI.orchestration.sec import SourceError

ZONE = ZoneInfo("America/Chicago")
POLICY = "fred_next_day_chicago_v1"
OPEN_END = date(9999, 12, 31)


class VintageLimit(SourceError):
    """Provider requires smaller real-time windows, not smaller result pages."""


def series_id(value):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_]{1,80}", value):
        raise ValueError("Invalid FRED series identifier")
    return value


def next_day(day):
    return datetime.combine(day + timedelta(days=1), daytime.min, ZONE).astimezone(
        timezone.utc
    )


class FredHttp:
    def __init__(self, api_key=None, session=None, sleep=time.sleep):
        import requests

        self.key = api_key or os.environ.get("FRED_API_KEY", "")
        if not re.fullmatch(r"[a-z0-9]{32}", self.key):
            raise ValueError("FRED_API_KEY must be configured")
        self.session = session or requests.Session()
        self.sleep = sleep
        self.last_request = 0

    def get(self, endpoint, params, record, heartbeat=lambda n: None):
        import requests

        allowed = {
            "series_id",
            "file_type",
            "realtime_start",
            "realtime_end",
            "observation_start",
            "observation_end",
            "output_type",
            "units",
            "sort_order",
            "limit",
            "offset",
        }
        if endpoint not in {"series", "series/observations"} or set(params) - allowed:
            raise ValueError("Unsupported FRED request")
        safe = {**params, "file_type": "json"}
        for attempt in range(1, 4):
            heartbeat(attempt)
            self.sleep(max(0, self.last_request + 0.6 - time.monotonic()))
            self.last_request = time.monotonic()
            try:
                response = self.session.get(
                    "https://api.stlouisfed.org/fred/" + endpoint,
                    params={**safe, "api_key": self.key},
                    timeout=(10, 45),
                    allow_redirects=False,
                )
            except (requests.Timeout, requests.ConnectionError):
                if attempt == 3:
                    raise SourceError("FRED transport failed") from None
                self.sleep(2**attempt + random.random())
                continue
            # FRED errors could echo credentials. Persist sanitized status, never the
            # request URL, auth query parameter or error body containing a key.
            redacted = (
                response.status_code != 200 or self.key.encode() in response.content
            )
            payload = (
                canonical_bytes(
                    {"status": response.status_code, "error_body_redacted": True}
                )
                if redacted
                else response.content
            )
            raw = record(
                payload,
                {
                    "endpoint": endpoint,
                    "params": safe,
                    "status": response.status_code,
                    "body_redacted": redacted,
                },
            )
            if response.status_code == 200:
                if redacted:
                    raise SourceError("Credential echoed by provider")
                return json.loads(payload, parse_float=Decimal), raw
            if response.status_code != 429 and not 500 <= response.status_code < 600:
                if (
                    response.status_code == 400
                    and b"exceeds the maximum number of vintage dates"
                    in response.content
                ):
                    raise VintageLimit("FRED vintage window limit")
                raise SourceError("FRED non-retryable response")
            if attempt == 3:
                raise SourceError("FRED retry budget exhausted")
            delay = 2**attempt + random.random()
            retry = response.headers.get("Retry-After")
            if retry:
                try:
                    delay = max(delay, float(retry))
                except ValueError:
                    delay = max(
                        delay,
                        (
                            parsedate_to_datetime(retry) - datetime.now(timezone.utc)
                        ).total_seconds(),
                    )
            if delay > 120:
                raise SourceError("FRED deferred by Retry-After")
            self.sleep(delay)
        raise SourceError("FRED request failed")


def parse_observations(payload, identifier, unit, raw_id, start, end, today):
    if payload.get("units") != "lin" or int(payload.get("output_type", 0)) != 1:
        raise ValueError("Untransformed real-time interval output required")
    if (
        payload.get("realtime_start") != "1776-07-04"
        or payload.get("realtime_end") != "9999-12-31"
    ):
        raise ValueError(
            "Clipped real-time intervals cannot be used as original vintages"
        )
    result = []
    for item in payload["observations"]:
        observed = date.fromisoformat(item["date"])
        vintage = date.fromisoformat(item["realtime_start"])
        through = date.fromisoformat(item["realtime_end"])
        if (
            not start <= observed <= end
            or not observed <= vintage <= today
            or through < vintage
        ):
            raise ValueError("Invalid observation/vintage range")
        value = None if item["value"] == "." else Decimal(item["value"])
        if value is not None and not value.is_finite():
            raise ValueError("Nonfinite FRED observation")
        status = "missing" if value is None else "valid"
        key = content_hash([identifier, str(observed), str(vintage), str(through)])
        result.append(
            {
                "observation_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "fred:" + key)),
                "series_id": identifier,
                "observation_date": observed,
                "vintage_date": vintage,
                "source_valid_through": through,
                "vintage_at": datetime.combine(vintage, daytime.min, ZONE).astimezone(
                    timezone.utc
                ),
                "available_at": next_day(vintage),
                "available_to": None
                if through == OPEN_END
                else next_day(through + timedelta(days=1)),
                "value": value,
                "status": status,
                "unit": unit,
                "source_key": key,
                "raw_id": raw_id,
            }
        )
    return result


def stitch_intervals(left, right, boundary):
    """Undo only clipping at a requested window edge; keep real revisions."""
    tails = {r["date"]: r for r in left if r["realtime_end"] == str(boundary)}
    following = str(boundary + timedelta(days=1))
    result = list(left)
    for row in right:
        previous = tails.get(row["date"])
        if (
            previous is not None
            and row["realtime_start"] == following
            and row["value"] == previous["value"]
        ):
            previous["realtime_end"] = row["realtime_end"]
        else:
            result.append(row)
    return result


def fetch_history(
    client,
    record,
    heartbeat,
    source_id,
    identifier,
    unit,
    start,
    end,
    today,
    page_limit,
    max_pages,
):
    raw_ids, budget = [], [max_pages]

    def fetch(lo, hi):
        # This adapter accepts realized observations only (vintage >= period
        # start, checked by parse_observations), never forecast series.
        if hi < start:
            return []
        rows, offset, total = [], 0, None
        while True:
            if budget[0] <= 0:
                raise ValueError("FRED request budget exhausted")
            budget[0] -= 1
            params = {
                "series_id": source_id,
                "realtime_start": str(lo),
                "realtime_end": str(hi),
                "observation_start": str(start),
                "observation_end": str(end),
                "output_type": 1,
                "units": "lin",
                "sort_order": "asc",
                "limit": page_limit,
                "offset": offset,
            }
            try:
                page, raw = client.get("series/observations", params, record, heartbeat)
            except VintageLimit:
                # Split across known calendar time; 9999 is an open-end sentinel.
                effective_hi = min(hi, today)
                if lo >= effective_hi:
                    raise ValueError("Cannot split FRED vintage window") from None
                middle = lo + (effective_hi - lo) // 2
                return stitch_intervals(
                    fetch(lo, middle), fetch(middle + timedelta(days=1), hi), middle
                )
            raw_ids.append(str(raw))
            if (
                page.get("units") != "lin"
                or int(page.get("output_type", 0)) != 1
                or page.get("realtime_start") != str(lo)
                or page.get("realtime_end") != str(hi)
            ):
                raise ValueError("Unexpected FRED real-time window")
            count, batch = int(page["count"]), page["observations"]
            if (
                count < 0
                or int(page["offset"]) != offset
                or (total is not None and count != total)
                or len(batch) > page_limit
                or offset + len(batch) > count
                or (not batch and count != 0)
            ):
                raise ValueError("FRED incomplete or changed pagination")
            total = count
            for row in batch:
                if (
                    not lo
                    <= date.fromisoformat(row["realtime_start"])
                    <= date.fromisoformat(row["realtime_end"])
                    <= hi
                ):
                    raise ValueError("Observation outside requested vintage window")
            rows.extend(dict(r) for r in batch)
            offset += len(batch)
            if offset == total:
                if len({(r["date"], r["realtime_start"]) for r in rows}) != len(rows):
                    raise ValueError("FRED overlapping pages")
                return rows

    items = fetch(date(1776, 7, 4), OPEN_END)
    if not items:
        raise ValueError("Empty FRED history")
    previous = {}
    for item in sorted(items, key=lambda r: (r["date"], r["realtime_start"])):
        if previous.get(item["date"], "") >= item["realtime_start"]:
            raise ValueError("Overlapping FRED vintage lifetimes")
        previous[item["date"]] = item["realtime_end"]
    # All original HTTP pages remain archived. This derived manifest records the
    # exact parents needed to reconstruct rows spanning multiple raw responses.
    payload = {
        "units": "lin",
        "output_type": 1,
        "realtime_start": "1776-07-04",
        "realtime_end": "9999-12-31",
        "observations": items,
        "parent_raw_ids": raw_ids,
        "reconstruction": "adjacent_equal_interval_v1",
    }
    raw = record(
        canonical_bytes(payload),
        {
            "endpoint": "derived-history",
            "format": "fred-reconstructed-intervals-v1",
            "series_id": identifier,
        },
    )
    rows = parse_observations(payload, identifier, unit, raw, start, end, today)
    if len({(r["observation_date"], r["vintage_date"]) for r in rows}) != len(rows):
        raise ValueError("FRED overlapping reconstructed history")
    return rows


def collect_fred(
    store,
    archive,
    client,
    catalog,
    code_sha,
    *,
    page_limit=10000,
    max_pages=100,
    now=None,
):
    now = now or datetime.now(timezone.utc)
    today = now.astimezone(ZONE).date()
    identifier = catalog["series_id"]
    start = catalog["observation_start"]
    end = min(catalog.get("observation_end") or today, today)
    if not 1 <= page_limit <= 100000 or max_pages <= 0 or start > end:
        raise ValueError("Invalid FRED collection range or page budget")
    with store.lock("source_http", "fred") as source_lock:
        if not source_lock:
            return {"series_id": identifier, "status": "locked"}
        with store.lock("fred_vintage", identifier) as acquired:
            if not acquired:
                return {"series_id": identifier, "status": "locked"}
            run = store.start(
                "fred_vintage",
                identifier,
                FetchRange(start, end),
                code_sha,
                {
                    "availability_policy": POLICY,
                    "output_type": 1,
                    "units": "lin",
                    "page_limit": page_limit,
                    "max_pages": max_pages,
                },
            )
            last_raw = None

            def record(payload, metadata):
                nonlocal last_raw
                checksum, uri = archive.put(payload)
                last_raw = store.raw(
                    run,
                    checksum,
                    uri,
                    datetime.now(timezone.utc),
                    metadata,
                    source="fred",
                    format="redacted-error-json-v1"
                    if metadata.get("body_redacted")
                    else metadata.get("format", "http-response-json-v1"),
                )
                return last_raw

            def heartbeat(n):
                store.heartbeat(run, n)

            try:
                payload, _ = client.get(
                    "series",
                    {"series_id": catalog["source_series_id"]},
                    record,
                    heartbeat,
                )
                metadata = payload["seriess"]
                if (
                    len(metadata) != 1
                    or metadata[0]["id"] != catalog["source_series_id"]
                ):
                    raise ValueError("FRED metadata identity mismatch")
                metadata = metadata[0]
                if any(
                    not metadata.get(k)
                    for k in ["units", "frequency", "title", "seasonal_adjustment"]
                ):
                    raise ValueError("Incomplete series metadata")
                rows = fetch_history(
                    client,
                    record,
                    heartbeat,
                    catalog["source_series_id"],
                    identifier,
                    metadata["units"],
                    start,
                    end,
                    today,
                    page_limit,
                    max_pages,
                )
                if not any(r["status"] == "valid" for r in rows):
                    raise ValueError("No valid FRED observations")
                inserted = store.commit_series(catalog, metadata, rows, run, start, end)
                return {
                    "series_id": identifier,
                    "run_id": run,
                    "status": "success",
                    "observations": len(rows),
                    "inserted": inserted,
                }
            except (ValueError, KeyError, TypeError, ArithmeticError):
                store.quarantine(
                    run,
                    last_raw,
                    [{"reason": "invalid_fred_schema_pagination_or_history"}],
                    0,
                )
                return {"series_id": identifier, "run_id": run, "status": "quarantined"}
            except Exception as exc:
                store.finish(run, "failed", type(exc).__name__)
                return {
                    "series_id": identifier,
                    "run_id": run,
                    "status": "failed",
                    "error_class": type(exc).__name__,
                }
