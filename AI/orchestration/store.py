"""PostgreSQL transactions, advisory ownership, and append-only run lineage."""

import hashlib
import json
import uuid
from contextlib import contextmanager
from datetime import datetime, time, timezone
from decimal import Decimal
from pathlib import Path

from AI.orchestration.core import content_hash, contiguous_end


def connect(db="db"):
    from AI.libs.database.connection import get_db_conn

    return get_db_conn(db)


def migrate(db="db"):
    conn = connect(db)
    try:
        with conn, conn.cursor() as cur:
            cur.execute("SELECT pg_advisory_xact_lock(724219001)")
            cur.execute("SELECT to_regclass('ingestion.schema_version')")
            applied = []
            if cur.fetchone()[0]:
                cur.execute(
                    "SELECT version FROM ingestion.schema_version ORDER BY version"
                )
                applied = [row[0] for row in cur.fetchall()]
            files = sorted(
                (Path(__file__).parents[1] / "migrations").glob("[0-9][0-9][0-9]_*.sql")
            )
            versions = [int(p.name.split("_")[0]) for p in files]
            if applied != versions[: len(applied)] or len(set(versions)) != len(
                versions
            ):
                raise RuntimeError("Unsupported ingestion schema version")
            for path in files[len(applied) :]:
                cur.execute(path.read_text(encoding="utf-8"))
            return versions[-1]
    finally:
        conn.close()


class Store:
    def __init__(self, db="db", connection_factory=None):
        self.db = db
        self.connection_factory = connection_factory or (lambda: connect(db))

    @contextmanager
    def transaction(self):
        conn = self.connection_factory()
        try:
            with conn, conn.cursor() as cur:
                cur.execute("SET LOCAL TIME ZONE 'UTC'")
                yield cur
        finally:
            conn.close()

    def query(self, query, params=()):
        with self.transaction() as cur:
            cur.execute(query, params)
            names = [c[0] for c in cur.description]
            return [dict(zip(names, row)) for row in cur.fetchall()]

    @contextmanager
    def lock(self, dataset, partition):
        # Session lock is released by PostgreSQL on connection/process death.
        key = int.from_bytes(
            hashlib.sha256(f"{dataset}\0{partition}".encode()).digest()[:8],
            "big",
            signed=True,
        )
        conn = self.connection_factory()
        conn.autocommit = True
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT pg_try_advisory_lock(%s)", (key,))
                acquired = cur.fetchone()[0]
            if acquired:
                with self.transaction() as cur:
                    # Ownership proves the former process no longer holds the lock.
                    cur.execute(
                        """UPDATE ingestion.dataset_run SET status='interrupted',
                        finished_at=now(), error_class='LostWorker'
                        WHERE dataset_id=%s AND partition_key=%s AND status='running'""",
                        (dataset, partition),
                    )
            yield acquired
        finally:
            conn.close()

    def register(self, dataset, partition, calendar, start, end=None):
        allowed = {"price_us_daily": {"XNYS", "XNAS"}, "crypto_daily": {"24/7"}}
        if calendar not in allowed.get(dataset, set()):
            raise ValueError("Unsupported calendar for dataset")
        with self.transaction() as cur:
            cur.execute(
                """INSERT INTO ingestion.dataset_partition
                (dataset_id, partition_key, calendar, start_date, end_date)
                VALUES (%s,%s,%s,%s,%s) ON CONFLICT (dataset_id, partition_key)
                DO UPDATE SET calendar=excluded.calendar, start_date=excluded.start_date,
                    end_date=excluded.end_date, active=true""",
                (dataset, partition, calendar, start, end),
            )
            # Changing the verified range invalidates its prior continuity claim.
            cur.execute(
                "DELETE FROM ingestion.dataset_watermark WHERE dataset_id=%s AND partition_key=%s",
                (dataset, partition),
            )

    def partitions(self, dataset=None, partition=None):
        return self.query(
            """SELECT * FROM ingestion.dataset_partition WHERE active
            AND (%s IS NULL OR dataset_id=%s) AND (%s IS NULL OR partition_key=%s)
            ORDER BY dataset_id, partition_key""",
            (dataset, dataset, partition, partition),
        )

    def observed(self, dataset, partition):
        return {
            r["session_date"]
            for r in self.query(
                """SELECT DISTINCT session_date
            FROM ingestion.daily_bar WHERE dataset_id=%s AND partition_key=%s""",
                (dataset, partition),
            )
        }

    def legacy_observed(self, dataset, partition, start, end):
        table = {"price_us_daily": "price_data", "crypto_daily": "crypto_price_data"}[
            dataset
        ]
        return {
            r["session_date"]
            for r in self.query(
                f"SELECT DISTINCT date::date AS session_date FROM public.{table} "
                "WHERE ticker=%s AND date::date BETWEEN %s AND %s",
                (partition, start, end),
            )
        }

    def start(self, dataset, partition, span, code_sha, config):
        run_id = str(uuid.uuid4())
        with self.transaction() as cur:
            cur.execute(
                """INSERT INTO ingestion.dataset_run
                (run_id,dataset_id,partition_key,requested_start,requested_end,status,code_sha,config_hash,metadata)
                VALUES (%s,%s,%s,%s,%s,'running',%s,%s,%s::jsonb)""",
                (
                    run_id,
                    dataset,
                    partition,
                    span.start if span else None,
                    span.end if span else None,
                    code_sha,
                    content_hash(config),
                    json.dumps(config),
                ),
            )
        return run_id

    def heartbeat(self, run_id, attempt):
        with self.transaction() as cur:
            cur.execute(
                "UPDATE ingestion.dataset_run SET heartbeat_at=now(), attempt=%s WHERE run_id=%s",
                (attempt, run_id),
            )

    def raw(
        self,
        run_id,
        checksum,
        uri,
        fetched_at,
        request,
        source="yahoo_sdk",
        format="dataframe-split-json-v1",
    ):
        raw_id = str(uuid.uuid4())
        with self.transaction() as cur:
            cur.execute(
                """INSERT INTO ingestion.raw_object
                (raw_id,run_id,checksum,object_uri,source,format,fetched_at,request_metadata)
                VALUES (%s,%s,%s,%s,%s,%s,%s,%s::jsonb)""",
                (
                    raw_id,
                    run_id,
                    checksum,
                    uri,
                    source,
                    format,
                    fetched_at,
                    json.dumps(request),
                ),
            )
        return raw_id

    def finish(self, run_id, status, error_class=None, fetched=0):
        with self.transaction() as cur:
            cur.execute(
                """UPDATE ingestion.dataset_run SET status=%s, error_class=%s,
                fetched_count=%s, finished_at=now(), heartbeat_at=now() WHERE run_id=%s""",
                (status, error_class, fetched, run_id),
            )

    def quarantine(self, run_id, raw_id, reasons, count):
        with self.transaction() as cur:
            cur.execute(
                "INSERT INTO ingestion.quarantine(run_id,raw_id,reasons) VALUES (%s,%s,%s::jsonb)",
                (run_id, raw_id, json.dumps(reasons)),
            )
            cur.execute(
                """UPDATE ingestion.dataset_run SET status='quarantined',
                fetched_count=%s, rejected_count=%s, finished_at=now(), heartbeat_at=now()
                WHERE run_id=%s""",
                (count, count, run_id),
            )

    def commit_bars(
        self, dataset, partition, rows, run_id, raw_id, available_at, expected
    ):
        with self.transaction() as cur:
            inserted = 0
            for row in rows:
                cur.execute(
                    """INSERT INTO ingestion.daily_bar
                    (dataset_id,partition_key,session_date,content_hash,open,high,low,close,volume,
                     adjusted_close,available_at,run_id,raw_id)
                    VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                    ON CONFLICT DO NOTHING""",
                    (
                        dataset,
                        partition,
                        row["date"],
                        content_hash(row),
                        row["open"],
                        row["high"],
                        row["low"],
                        row["close"],
                        row["volume"],
                        row.get("adjusted_close"),
                        available_at,
                        run_id,
                        raw_id,
                    ),
                )
                inserted += cur.rowcount
                # Compatibility tables remain readable by the existing Spring/AI stack.
                # PER/PBR are deliberately left unchanged: legacy fiscal dates are not PIT.
                if dataset == "price_us_daily":
                    cur.execute(
                        """INSERT INTO public.price_data
                        (ticker,date,open,high,low,close,volume,adjusted_close,amount)
                        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)
                        ON CONFLICT (date,ticker) DO UPDATE SET open=excluded.open, high=excluded.high,
                        low=excluded.low, close=excluded.close, volume=excluded.volume,
                        adjusted_close=excluded.adjusted_close, amount=excluded.amount""",
                        (
                            partition,
                            row["date"],
                            row["open"],
                            row["high"],
                            row["low"],
                            row["close"],
                            int(Decimal(row["volume"])),
                            row.get("adjusted_close"),
                            str(Decimal(row["close"]) * Decimal(row["volume"])),
                        ),
                    )
                elif dataset == "crypto_daily":
                    cur.execute(
                        """INSERT INTO public.crypto_price_data
                        (ticker,date,open,high,low,close,volume) VALUES (%s,%s,%s,%s,%s,%s,%s)
                        ON CONFLICT (date,ticker) DO UPDATE SET open=excluded.open, high=excluded.high,
                        low=excluded.low, close=excluded.close, volume=excluded.volume""",
                        (
                            partition,
                            row["date"],
                            row["open"],
                            row["high"],
                            row["low"],
                            row["close"],
                            row["volume"],
                        ),
                    )
            cur.execute(
                """SELECT DISTINCT session_date FROM ingestion.daily_bar
                WHERE dataset_id=%s AND partition_key=%s""",
                (dataset, partition),
            )
            through = contiguous_end(expected, {r[0] for r in cur.fetchall()})
            if through:
                cur.execute(
                    """INSERT INTO ingestion.dataset_watermark
                    (dataset_id,partition_key,last_success_event_time,last_success_run_id)
                    VALUES (%s,%s,%s,%s) ON CONFLICT (dataset_id,partition_key) DO UPDATE SET
                    last_success_event_time=excluded.last_success_event_time,
                    last_success_run_id=excluded.last_success_run_id, updated_at=now()""",
                    (
                        dataset,
                        partition,
                        datetime.combine(through, time.min, timezone.utc),
                        run_id,
                    ),
                )
            cur.execute(
                """UPDATE ingestion.dataset_run SET status='success', fetched_count=%s,
                inserted_count=%s, finished_at=now(), heartbeat_at=now() WHERE run_id=%s""",
                (len(rows), inserted, run_id),
            )
        return inserted

    def pulse(self, scheduler_id):
        with self.transaction() as cur:
            cur.execute(
                """INSERT INTO ingestion.scheduler_heartbeat(scheduler_id) VALUES (%s)
                ON CONFLICT (scheduler_id) DO UPDATE SET heartbeat_at=now()""",
                (scheduler_id,),
            )
