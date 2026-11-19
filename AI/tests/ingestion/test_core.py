import json
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timezone

import pytest

from AI.orchestration.archive import RawArchive
from AI.orchestration.core import (
    FetchRange,
    completed_sessions,
    content_hash,
    contiguous_end,
    plan_ranges,
    validate_bars,
)
from AI.orchestration.market import YahooDaily


def bar(day="2026-09-01", **changes):
    return {
        "date": day,
        "open": "10",
        "high": "12",
        "low": "9",
        "close": "11",
        "volume": "100",
        "adjusted_close": "11",
        **changes,
    }


def test_hole_behind_latest_and_overlap():
    days = [date(2026, 9, i) for i in [1, 2, 3, 4, 8]]
    observed = set(days) - {days[1]}
    assert plan_ranges(days, observed) == [FetchRange(days[1], days[1])]
    assert plan_ranges(days, observed, overlap=2) == [
        FetchRange(days[1], days[1]),
        FetchRange(days[3], days[4]),
    ]
    assert contiguous_end(days, observed) == days[0]


def test_ten_day_outage_batches_and_rerun():
    days = [date(2026, 9, i) for i in range(1, 11)]
    spans = plan_ranges(days, set(), batch_size=3)
    assert len(spans) == 4
    assert spans[-1] == FetchRange(days[-1], days[-1])
    assert plan_ranges(days, days) == []


def test_nyse_holiday_and_early_close():
    before = datetime(2025, 11, 28, 19, 29, tzinfo=timezone.utc)
    after = datetime(2025, 11, 28, 19, 30, tzinfo=timezone.utc)
    # Thanksgiving closed; Black Friday closes at 18:00 UTC, available 90m later.
    assert (
        completed_sessions("XNYS", date(2025, 11, 27), date(2025, 11, 28), before) == []
    )
    assert completed_sessions(
        "XNYS", date(2025, 11, 27), date(2025, 11, 28), after
    ) == [date(2025, 11, 28)]


def test_crypto_not_yet_closed_and_timezone_required():
    now = datetime(2026, 9, 22, 1, 29, tzinfo=timezone.utc)
    assert completed_sessions("24/7", date(2026, 9, 20), date(2026, 9, 22), now) == [
        date(2026, 9, 20)
    ]
    with pytest.raises(ValueError):
        completed_sessions(
            "24/7", date(2026, 9, 20), date(2026, 9, 22), now.replace(tzinfo=None)
        )


@pytest.mark.parametrize(
    "changes,reason",
    [
        ({"low": "20"}, "invalid_ohlcv"),
        ({"volume": "-1"}, "invalid_ohlcv"),
        ({"close": "NaN"}, "non_finite"),
        ({"open": "Infinity"}, "non_finite"),
        ({"adjusted_close": "0"}, "invalid_adjusted_close"),
        ({"open": None}, "invalid_schema"),
    ],
)
def test_invalid_data_is_not_validated(changes, reason):
    assert reason in validate_bars([bar(**changes)], [date(2026, 9, 1)])


def test_empty_missing_duplicate_unexpected():
    assert validate_bars([], [date(2026, 9, 1)]) == ["empty_response"]
    assert "missing_sessions" in validate_bars(
        [bar()], [date(2026, 9, 1), date(2026, 9, 2)]
    )
    assert "duplicate_session" in validate_bars([bar(), bar()], [date(2026, 9, 1)])
    assert "unexpected_session" in validate_bars([bar()], [])


def test_archive_concurrency_and_corruption(tmp_path):
    archive = RawArchive(tmp_path)
    with ThreadPoolExecutor(max_workers=4) as workers:
        results = list(workers.map(archive.put, [b"payload"] * 8))
    assert len(set(results)) == 1
    from pathlib import Path

    path = Path(results[0][1])
    assert path.read_bytes() == b"payload"
    path.write_bytes(b"corrupt")
    with pytest.raises(IOError):
        archive.put(b"payload")


def test_canonical_hash_is_key_order_independent():
    assert content_hash({"a": 1, "b": 2}) == content_hash({"b": 2, "a": 1})


def test_sdk_contract_before_normalization():
    payload = json.dumps(
        {
            "columns": [
                "Open",
                "High",
                "Low",
                "Close",
                "Volume",
                "Adj Close",
                "Dividends",
            ],
            "index": ["2026-09-01T04:00:00.000Z"],
            "data": [[10, 12, 9, 11, 100, 11, 0]],
        }
    ).encode()
    assert YahooDaily().parse(payload) == [bar()]


def test_sql_migration_parses():
    from pathlib import Path

    from pglast import parse_sql

    for path in (Path(__file__).parents[2] / "migrations").glob("*.sql"):
        assert parse_sql(path.read_text(encoding="utf-8"))
