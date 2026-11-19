import json
from datetime import date, datetime, timezone

import pytest
from test_fred import observation, page

from AI.orchestration.archive import RawArchive
from AI.orchestration.fred import collect_fred


class Client:
    def __init__(self, rows, unit="Billions of Dollars", broken=False):
        self.rows = rows
        self.unit = unit
        self.broken = broken
        self.calls = []

    def get(self, endpoint, params, record, heartbeat):
        self.calls.append((endpoint, params))
        if endpoint == "series":
            payload = {
                "seriess": [
                    {
                        "id": "GDP",
                        "title": "Synthetic GDP",
                        "units": self.unit,
                        "frequency": "Quarterly",
                        "seasonal_adjustment": "SAAR",
                    }
                ]
            }
        else:
            offset = params["offset"]
            limit = params["limit"]
            batch = [] if self.broken and offset else self.rows[offset : offset + limit]
            payload = page(batch, count=len(self.rows), offset=offset)
        raw = record(
            json.dumps(payload).encode(), {"endpoint": endpoint, "status": 200}
        )
        return payload, raw


def collect(store, tmp_path, rows, **kwargs):
    store.register_series("GDP", "growth", date(2020, 1, 1), date(2020, 12, 31))
    client = Client(rows, **kwargs)
    result = collect_fred(
        store,
        RawArchive(tmp_path),
        client,
        store.series_catalog(["GDP"])[0],
        "test",
        page_limit=1,
    )
    return result, client


def query(store, cutoff, data_as_of=None):
    return store.series(
        ["GDP"],
        start="2020-01-01",
        end="2020-12-31",
        as_of=cutoff,
        data_as_of=data_as_of,
    )


def test_vintage_boundary_pagination_and_idempotence(store, tmp_path):
    rows = [
        observation(through="2020-05-27"),
        observation(vintage="2020-05-28", value="110"),
    ]
    result, client = collect(store, tmp_path, rows)
    assert result["status"] == "success" and result["inserted"] == 2
    assert [p["offset"] for e, p in client.calls if e == "series/observations"] == [
        0,
        1,
    ]
    assert query(store, "2020-04-30T04:59:59Z") == []
    assert query(store, "2020-04-30T05:00:00Z")[0]["value"] == 100
    assert query(store, "2020-05-29T04:59:59Z")[0]["value"] == 100
    assert query(store, "2020-05-29T05:00:00Z")[0]["value"] == 110
    assert collect(store, tmp_path, rows)[0]["inserted"] == 0
    assert (
        store.query("SELECT count(*) AS n FROM ingestion.series_observation")[0]["n"]
        == 2
    )


def test_lifetime_revision_and_data_version_cutoff(store, tmp_path):
    assert collect(store, tmp_path, [observation()])[0]["status"] == "success"
    first = datetime.now(timezone.utc)
    assert (
        collect(store, tmp_path, [observation(through="2020-05-27")])[0]["status"]
        == "success"
    )
    # Withdrawn value without a successor must not forward-fill indefinitely.
    assert query(store, "2020-06-01T00:00:00Z") == []
    assert query(store, "2020-06-01T00:00:00Z", data_as_of=first)[0]["value"] == 100
    # A source lifetime can be restored; latest scan membership, not first ingestion, wins.
    assert collect(store, tmp_path, [observation()])[0]["inserted"] == 0
    assert query(store, "2020-06-01T00:00:00Z")[0]["value"] == 100


def test_later_missing_does_not_reveal_previous_valid_value(store, tmp_path):
    rows = [
        observation(through="2020-05-27"),
        observation(vintage="2020-05-28", value="."),
    ]
    assert collect(store, tmp_path, rows)[0]["status"] == "success"
    later = query(store, "2020-06-01T00:00:00Z")[0]
    assert later["status"] == "missing" and later["value"] is None


def test_empty_or_partial_page_cannot_advance_watermark(store, tmp_path):
    rows = [
        observation(through="2020-05-27"),
        observation(vintage="2020-05-28", value="110"),
    ]
    assert collect(store, tmp_path, rows, broken=True)[0]["status"] == "quarantined"
    assert not store.query(
        "SELECT * FROM ingestion.dataset_watermark WHERE dataset_id='fred_vintage'"
    )
    assert not store.query("SELECT * FROM ingestion.series_observation")
    assert collect(store, tmp_path, [])[0]["status"] == "quarantined"


def test_changed_unit_or_vintage_value_is_quarantined(store, tmp_path):
    collect(store, tmp_path, [observation()])
    assert (
        collect(store, tmp_path, [observation(value="999")])[0]["status"]
        == "quarantined"
    )
    assert (
        collect(store, tmp_path, [observation()], unit="Millions")[0]["status"]
        == "quarantined"
    )
    assert query(store, "2020-06-01T00:00:00Z")[0]["value"] == 100
    assert store.series_catalog(["GDP"])[0]["unit"] == "Billions of Dollars"


def test_removed_observation_is_absent_after_complete_rescan(store, tmp_path):
    collect(
        store,
        tmp_path,
        [observation(), observation(day="2020-04-01", vintage="2020-07-30")],
    )
    assert len(query(store, "2021-01-01T00:00:00Z")) == 2
    collect(store, tmp_path, [observation(day="2020-04-01", vintage="2020-07-30")])
    assert len(query(store, "2021-01-01T00:00:00Z")) == 1


def test_asof_required_and_silver_is_immutable(store, tmp_path):
    collect(store, tmp_path, [observation()])
    with pytest.raises(TypeError):
        store.series(["GDP"], start="2020-01-01", end="2020-12-31")
    with pytest.raises(ValueError):
        query(store, "2020-05-01")
    with pytest.raises(Exception), store.transaction() as cur:
        cur.execute("UPDATE ingestion.series_observation SET value=999")


def test_stale_collection_and_failed_run_visible(store, tmp_path):
    collect(store, tmp_path, [observation()])
    assert store.series_status()[0]["stale_collection"] is False
    collect(store, tmp_path, [])
    assert store.series_status()[0]["last_run_status"] == "quarantined"
    # Widened registration requires another scan covering the larger interval.
    store.register_series("GDP", "growth", date(2018, 1, 1), date(2020, 12, 31))
    assert store.series_status()[0]["stale_collection"] is True


class WindowClient(Client):
    def get(self, endpoint, params, record, heartbeat):
        from AI.orchestration.fred import VintageLimit

        if endpoint == "series":
            return super().get(endpoint, params, record, heartbeat)
        lo = date.fromisoformat(params["realtime_start"])
        hi = date.fromisoformat(params["realtime_end"])
        if (min(hi, date(2021, 1, 1)) - lo).days > 300:
            raise VintageLimit("synthetic limit")
        clipped = []
        for r in self.rows:
            lower = max(lo, date.fromisoformat(r["realtime_start"]))
            upper = min(hi, date.fromisoformat(r["realtime_end"]))
            if lower <= upper:
                clipped.append(
                    {**r, "realtime_start": str(lower), "realtime_end": str(upper)}
                )
        payload = page(clipped)
        payload.update(realtime_start=str(lo), realtime_end=str(hi))
        return payload, record(json.dumps(payload).encode(), {"endpoint": endpoint})


def test_split_windows_full_history_and_atomic_budget_failure(store, tmp_path):
    store.register_series("GDP", "growth", date(2020, 1, 1), date(2020, 12, 31))
    rows = [
        observation(through="2020-05-27"),
        observation(vintage="2020-05-28", value="110"),
    ]
    args = (
        store,
        RawArchive(tmp_path),
        WindowClient(rows),
        store.series_catalog(["GDP"])[0],
        "test",
    )
    result = collect_fred(*args, now=datetime(2021, 1, 1, tzinfo=timezone.utc))
    assert result["status"] == "success" and result["inserted"] == 2
    assert query(store, "2020-05-01T00:00:00Z")[0]["value"] == 100
    assert query(store, "2020-06-01T00:00:00Z")[0]["value"] == 110
    first = store.query("SELECT last_success_run_id FROM ingestion.dataset_watermark")[
        0
    ]
    result = collect_fred(
        *args, max_pages=1, now=datetime(2021, 1, 1, tzinfo=timezone.utc)
    )
    assert result["status"] == "quarantined"
    assert (
        store.query("SELECT last_success_run_id FROM ingestion.dataset_watermark")[0]
        == first
    )


def test_overlapping_lifetimes_are_quarantined(store, tmp_path):
    result, _ = collect(
        store, tmp_path, [observation(), observation(vintage="2020-05-28", value="110")]
    )
    assert result["status"] == "quarantined"
    assert store.query("SELECT * FROM ingestion.dataset_watermark") == []
