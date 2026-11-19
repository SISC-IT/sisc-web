import json
from datetime import date

import pytest

from AI.orchestration.fred import FredHttp, SourceError, next_day, parse_observations
from AI.orchestration.sec import utc

KEY = "a" * 32


def observation(
    vintage="2020-04-29", through="9999-12-31", value="100", day="2020-01-01"
):
    return {
        "realtime_start": vintage,
        "realtime_end": through,
        "date": day,
        "value": value,
    }


def page(rows, count=None, offset=0):
    return {
        "units": "lin",
        "output_type": 1,
        "realtime_start": "1776-07-04",
        "realtime_end": "9999-12-31",
        "count": len(rows) if count is None else count,
        "offset": offset,
        "observations": rows,
    }


def parse(payload):
    return parse_observations(
        payload,
        "GDP",
        "Billions of Dollars",
        "raw",
        date(2020, 1, 1),
        date(2020, 12, 31),
        date(2026, 9, 23),
    )


def test_date_precision_and_dst_policy():
    assert next_day(date(2020, 3, 8)) == utc("2020-03-09T05:00:00Z")
    assert next_day(date(2020, 11, 1)) == utc("2020-11-02T06:00:00Z")
    row = parse(page([observation(through="2020-05-27")]))[0]
    assert row["available_at"] == utc("2020-04-30T05:00:00Z")
    assert row["available_to"] == utc("2020-05-29T05:00:00Z")


def test_missing_and_infinite_values():
    assert parse(page([observation(value=".")]))[0]["status"] == "missing"
    with pytest.raises(ValueError):
        parse(page([observation(value="NaN")]))


def test_clipped_history_and_future_vintage_rejected():
    clipped = page([observation()])
    clipped["realtime_start"] = "2020-01-01"
    with pytest.raises(ValueError):
        parse(clipped)
    with pytest.raises(ValueError):
        parse(page([observation(vintage="2999-01-01")]))


class Response:
    def __init__(self, status, body=b"{}", headers=None):
        self.status_code = status
        self.content = body
        self.headers = headers or {}


class Session:
    def __init__(self, responses):
        self.responses = iter(responses)
        self.calls = []

    def get(self, *args, **kwargs):
        self.calls.append(kwargs)
        return next(self.responses)


def test_api_key_never_archived_even_on_echoed_error():
    session = Session([Response(400, KEY.encode())])
    captured = []
    client = FredHttp(KEY, session, lambda _: None)
    with pytest.raises(SourceError):
        client.get(
            "series",
            {"series_id": "GDP"},
            lambda body, meta: captured.append((body, meta)),
        )
    assert KEY not in str(captured)
    assert captured[0][1]["body_redacted"]
    assert session.calls[0]["params"]["api_key"] == KEY
    assert session.calls[0]["allow_redirects"] is False


def test_retry_after_and_200_raw_preservation():
    payload = json.dumps({"seriess": []}).encode()
    session = Session(
        [Response(429, KEY.encode(), {"Retry-After": "9"}), Response(200, payload)]
    )
    captured, waits = [], []

    def record(body, meta):
        captured.append((body, meta))
        return "raw"

    result, raw = FredHttp(KEY, session, waits.append).get(
        "series", {"series_id": "GDP"}, record
    )
    assert result == {"seriess": []} and raw == "raw"
    assert 9 in waits and captured[-1][0] == payload


def test_disallowed_request_and_missing_key():
    with pytest.raises(ValueError):
        FredHttp("invalid")
    client = FredHttp(KEY, Session([]), lambda _: None)
    with pytest.raises(ValueError):
        client.get("https://other.example", {}, lambda *_: None)
    with pytest.raises(ValueError):
        client.get("series", {"api_key": "other"}, lambda *_: None)


def test_vintage_limit_is_distinct_and_sanitized():
    from AI.orchestration.fred import VintageLimit

    session = Session(
        [Response(400, b"exceeds the maximum number of vintage dates " + KEY.encode())]
    )
    captured = []
    with pytest.raises(VintageLimit):
        FredHttp(KEY, session, lambda _: None).get(
            "series/observations", {}, lambda body, meta: captured.append((body, meta))
        )
    assert KEY not in str(captured)


def test_window_edges_preserve_original_vintage_and_real_revisions():
    from AI.orchestration.fred import stitch_intervals

    left = [observation(through="2020-05-27")]
    right = [
        observation(vintage="2020-05-28", through="2020-06-01"),
        observation(vintage="2020-06-02", value="110"),
    ]
    rows = stitch_intervals(left, right, date(2020, 5, 27))
    assert len(rows) == 2
    assert rows[0]["realtime_start"] == "2020-04-29"
    assert rows[0]["realtime_end"] == "2020-06-01"
    assert rows[1]["realtime_start"] == "2020-06-02"
    changed = stitch_intervals(
        [observation(through="2020-05-27")],
        [observation(vintage="2020-05-28", value="110")],
        date(2020, 5, 27),
    )
    assert len(changed) == 2
    # A gap must not be stitched over, even when the values agree.
    assert (
        len(
            stitch_intervals(
                [observation(through="2020-05-26")], right, date(2020, 5, 27)
            )
        )
        == 3
    )
