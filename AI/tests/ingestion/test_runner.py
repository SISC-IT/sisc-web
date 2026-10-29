from contextlib import contextmanager
from datetime import date, datetime, timezone
from types import SimpleNamespace

from AI.orchestration.archive import RawArchive
from AI.orchestration.core import canonical_bytes, contiguous_end
from AI.orchestration.legacy import JOBS, execute_jobs
from AI.orchestration.runner import run_all, run_partition


class MemoryStore:
    def __init__(self):
        self.runs, self.days, self.watermark = {}, set(), None
        self.locked = False
        self.raws = []
        self.fail_commit = False

    @contextmanager
    def lock(self, *args):
        yield not self.locked

    def observed(self, *args):
        return set(self.days)

    def legacy_observed(self, *args):
        return set(self.days)

    def start(self, *args):
        key = str(len(self.runs))
        self.runs[key] = "running"
        return key

    def heartbeat(self, *args):
        pass

    def raw(self, *args):
        self.raws.append(args)
        return "raw"

    def finish(self, run_id, status, *args):
        self.runs[run_id] = status

    def quarantine(self, run_id, *args):
        self.runs[run_id] = "quarantined"

    def commit_bars(
        self, dataset, partition, rows, run_id, raw_id, available, expected
    ):
        if self.fail_commit:
            raise RuntimeError("commit failed")
        self.days.update(date.fromisoformat(r["date"]) for r in rows)
        self.watermark = contiguous_end(expected, self.days)
        self.runs[run_id] = "success"


class Connector:
    def __init__(self, rows=None, error=None):
        self.rows = (
            rows
            if rows is not None
            else [
                {
                    "date": f"2026-09-{i:02}",
                    "open": "10",
                    "high": "12",
                    "low": "9",
                    "close": "11",
                    "volume": "100",
                }
                for i in range(1, 11)
            ]
        )
        self.error = error
        self.calls = 0

    def fetch(self, ticker, span):
        self.calls += 1
        if self.error:
            raise self.error
        return canonical_bytes(
            [
                r
                for r in self.rows
                if span.start <= date.fromisoformat(r["date"]) <= span.end
            ]
        )

    def parse(self, payload):
        import json

        return json.loads(payload)


PARTITION = {
    "dataset_id": "crypto_daily",
    "partition_key": "BTC-USD",
    "calendar": "24/7",
    "start_date": date(2026, 9, 1),
    "end_date": date(2026, 9, 10),
    "overlap_sessions": 5,
}
NOW = datetime(2026, 9, 12, tzinfo=timezone.utc)


def run(store, tmp_path, connector, **kwargs):
    return run_partition(
        store, RawArchive(tmp_path), connector, PARTITION, "test", now=NOW, **kwargs
    )


def test_recovery_and_idempotence(tmp_path):
    store, connector = MemoryStore(), Connector()
    assert run(store, tmp_path, connector)["status"] == "success"
    assert store.watermark == date(2026, 9, 10)
    assert run(store, tmp_path, connector)["status"] == "noop"
    assert connector.calls == 1


def test_zero_rows_quarantine_without_watermark(tmp_path):
    store = MemoryStore()
    assert run(store, tmp_path, Connector([]))["status"] == "failed"
    assert list(store.runs.values()) == ["quarantined"]
    assert store.watermark is None and len(store.raws) == 1


def test_commit_failure_cannot_advance_watermark(tmp_path):
    store = MemoryStore()
    store.fail_commit = True
    assert run(store, tmp_path, Connector())["status"] == "failed"
    assert store.watermark is None
    assert list(store.runs.values()) == ["failed"]


def test_bounded_transient_retry(tmp_path):
    connector = Connector(error=TimeoutError())
    waits = []
    run(MemoryStore(), tmp_path, connector, sleep=waits.append)
    assert connector.calls == 3 and len(waits) == 2


def test_permanent_error_not_retried(tmp_path):
    connector = Connector(error=ValueError("schema"))
    run(MemoryStore(), tmp_path, connector)
    assert connector.calls == 1


def test_partition_lock_prevents_duplicate_fetch(tmp_path):
    store, connector = MemoryStore(), Connector()
    store.locked = True
    assert run(store, tmp_path, connector)["status"] == "locked"
    assert connector.calls == 0


def test_later_range_does_not_skip_prior_gap(tmp_path):
    store = MemoryStore()
    run(store, tmp_path, Connector(), start=date(2026, 9, 5))
    assert store.watermark is None
    run(store, tmp_path, Connector())
    assert store.watermark == date(2026, 9, 10)


def test_failed_partition_does_not_stop_next(tmp_path):
    partitions = [{**PARTITION, "calendar": "invalid"}, PARTITION]
    results = run_all(
        MemoryStore(), RawArchive(tmp_path), Connector(), partitions, "test", now=NOW
    )
    assert [r["status"] for r in results] == ["failed", "success"]


def test_legacy_jobs_isolated_and_never_claim_verified_success():
    args = SimpleNamespace(db="db", repair=False, **{job.skip: False for job in JOBS})
    seen = []

    def invoke(job, *_):
        seen.append(job.name)
        if job.name == "macro":
            raise RuntimeError("failure")

    store = MemoryStore()
    results = execute_jobs(args, ["AAPL"], store=store, invoke_job=invoke)
    assert len(seen) == 8
    assert results[0]["status"] == "failed"
    assert all(r["status"] == "unverified" for r in results[1:])
    assert store.watermark is None
