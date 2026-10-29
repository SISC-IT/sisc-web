"""Pure planning/validation helpers; no network or database at import time."""

import hashlib
import json
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation


@dataclass(frozen=True)
class FetchRange:
    start: date
    end: date  # inclusive throughout our API; provider adapts to exclusive end


def canonical_bytes(value):
    return json.dumps(
        value,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
        allow_nan=False,
    ).encode("utf-8")


def content_hash(value):
    return hashlib.sha256(canonical_bytes(value)).hexdigest()


def plan_ranges(expected, observed, overlap=0, batch_size=20):
    """Compare ALL expected sessions, including holes behind the watermark."""
    if overlap < 0 or batch_size <= 0:
        raise ValueError("Invalid overlap or batch size")
    sessions = sorted(set(expected))
    wanted = set(sessions) - set(observed)
    if overlap:
        wanted.update(sessions[-overlap:])
    groups, current = [], []
    for session in sessions:
        if session not in wanted or len(current) == batch_size:
            if current:
                groups.append(FetchRange(current[0], current[-1]))
                current = []
        if session in wanted:
            current.append(session)
    if current:
        groups.append(FetchRange(current[0], current[-1]))
    return groups


def contiguous_end(expected, observed):
    result = None
    for session in sorted(set(expected)):
        if session not in observed:
            break
        result = session
    return result


def completed_sessions(calendar, start, end, now=None):
    """Use exchange closes (DST/early closes included), plus 90m provider lag."""
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        raise ValueError("now must be timezone-aware")
    if end < start:
        return []
    if calendar == "24/7":
        sessions = [start + timedelta(days=i) for i in range((end - start).days + 1)]
        return [
            d
            for d in sessions
            if datetime.combine(
                d + timedelta(days=1), datetime.min.time(), timezone.utc
            )
            + timedelta(minutes=90)
            <= now
        ]
    import exchange_calendars as xcals

    # Calendar bounds are session labels, so include padding for holiday/weekend endpoints.
    cal = xcals.get_calendar(
        calendar,
        start=(start - timedelta(days=14)).isoformat(),
        end=(end + timedelta(days=14)).isoformat(),
    )
    return [
        s.date()
        for s in cal.sessions_in_range(start.isoformat(), end.isoformat())
        if cal.session_close(s).to_pydatetime() + timedelta(minutes=90) <= now
    ]


def validate_bars(rows, expected):
    reasons, seen = [], set()
    expected = set(expected)
    if not rows:
        return ["empty_response"]
    for row in rows:
        try:
            day = date.fromisoformat(row["date"])
            if day in seen:
                reasons.append("duplicate_session")
            seen.add(day)
            if day not in expected:
                reasons.append("unexpected_session")
            values = {
                k: Decimal(str(row[k]))
                for k in ("open", "high", "low", "close", "volume")
            }
            if not all(v.is_finite() for v in values.values()):
                reasons.append("non_finite")
                continue
            o, h, low, c, v = (
                values[k] for k in ("open", "high", "low", "close", "volume")
            )
            if not (0 < low <= min(o, c) <= max(o, c) <= h) or v < 0:
                reasons.append("invalid_ohlcv")
            if row.get("adjusted_close") is not None:
                adj = Decimal(str(row["adjusted_close"]))
                if not adj.is_finite() or adj <= 0:
                    reasons.append("invalid_adjusted_close")
        except (KeyError, ValueError, TypeError, InvalidOperation):
            reasons.append("invalid_schema")
    if expected - seen:
        reasons.append("missing_sessions")
    return sorted(set(reasons))
