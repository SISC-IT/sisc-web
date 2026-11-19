"""Long-form macro vintages; immutable values and observed lifetime revisions."""

from datetime import datetime, time, timezone

from AI.orchestration.fred import POLICY, series_id
from AI.orchestration.pit_store import PitStore
from AI.orchestration.sec import utc


class SeriesStore(PitStore):
    def register_series(self, identifier, category, start, end=None):
        identifier = series_id(identifier)
        if end and end < start:
            raise ValueError("Invalid observation range")
        with self.lock("fred_vintage", identifier) as acquired:
            if not acquired:
                raise ValueError("Series is currently being collected")
            with self.transaction() as cur:
                cur.execute(
                    """INSERT INTO ingestion.series_catalog(series_id,source_series_id,category,observation_start,observation_end)
                    VALUES (%s,%s,%s,%s,%s) ON CONFLICT (series_id) DO UPDATE SET
                    category=excluded.category,observation_start=excluded.observation_start,
                    observation_end=excluded.observation_end,active=true""",
                    (identifier, identifier, category, start, end),
                )

    def series_catalog(self, ids=None):
        rows = self.query(
            """SELECT * FROM ingestion.series_catalog WHERE active
            AND (%s IS NULL OR series_id=ANY(%s)) ORDER BY series_id""",
            (ids, ids),
        )
        if ids and set(ids) != {r["series_id"] for r in rows}:
            raise ValueError("Unknown or inactive series requested")
        return rows

    def commit_series(self, catalog, metadata, rows, run_id, start, end):
        from psycopg2.extras import execute_values

        identifier = catalog["series_id"]
        fields = [
            "observation_id",
            "series_id",
            "observation_date",
            "vintage_date",
            "source_valid_through",
            "vintage_at",
            "available_at",
            "available_to",
            "value",
            "status",
            "unit",
            "source_key",
            "raw_id",
        ]
        with self.transaction() as cur:
            cur.execute(
                "SELECT unit,frequency,seasonal_adjustment,availability_policy FROM ingestion.series_catalog WHERE series_id=%s FOR UPDATE",
                (identifier,),
            )
            current = cur.fetchone()
            if not current or current[3] != POLICY:
                raise ValueError("Unknown series policy")
            if current[0] is not None and current[:3] != (
                metadata["units"],
                metadata["frequency"],
                metadata["seasonal_adjustment"],
            ):
                raise ValueError(
                    "Source unit/frequency changed; explicit version migration required"
                )
            cur.execute(
                """UPDATE ingestion.series_catalog SET name=%s,unit=%s,frequency=%s,seasonal_adjustment=%s
                WHERE series_id=%s""",
                (
                    metadata["title"],
                    metadata["units"],
                    metadata["frequency"],
                    metadata["seasonal_adjustment"],
                    identifier,
                ),
            )
            cur.execute(
                "SELECT observation_date,vintage_date,value,status FROM ingestion.series_observation WHERE series_id=%s",
                (identifier,),
            )
            previous = {
                (d, v): (value, status) for d, v, value, status in cur.fetchall()
            }
            for r in rows:
                if r["series_id"] != identifier or r["unit"] != metadata["units"]:
                    raise ValueError("Observation identity mismatch")
                key = (r["observation_date"], r["vintage_date"])
                if key in previous and previous[key] != (r["value"], r["status"]):
                    raise ValueError(
                        "Existing vintage value changed; remediation required"
                    )
                previous[key] = (r["value"], r["status"])
            inserted = execute_values(
                cur,
                f"""INSERT INTO ingestion.series_observation ({",".join(fields)},run_id)
                VALUES %s ON CONFLICT DO NOTHING RETURNING observation_id""",
                [tuple(r[k] for k in fields) + (run_id,) for r in rows],
                fetch=True,
            )
            cur.execute(
                """INSERT INTO ingestion.series_coverage
                (run_id,series_id,requested_start,requested_end,row_count) VALUES (%s,%s,%s,%s,%s)""",
                (run_id, identifier, start, end, len(rows)),
            )
            execute_values(
                cur,
                "INSERT INTO ingestion.series_run_observation(run_id,observation_id) VALUES %s",
                [(run_id, r["observation_id"]) for r in rows],
            )
            cur.execute(
                """UPDATE ingestion.dataset_run SET status='success',fetched_count=%s,inserted_count=%s,
                finished_at=now(),heartbeat_at=now() WHERE run_id=%s""",
                (len(rows), len(inserted), run_id),
            )
            through = max(r["observation_date"] for r in rows if r["status"] == "valid")
            cur.execute(
                """INSERT INTO ingestion.dataset_watermark
                (dataset_id,partition_key,last_success_event_time,last_success_run_id)
                VALUES ('fred_vintage',%s,%s,%s) ON CONFLICT (dataset_id,partition_key) DO UPDATE SET
                last_success_event_time=excluded.last_success_event_time,
                last_success_run_id=excluded.last_success_run_id,updated_at=now()""",
                (identifier, datetime.combine(through, time.min, timezone.utc), run_id),
            )
        return len(inserted)

    def series(self, ids, *, start, end, as_of, data_as_of=None):
        """as_of: simulation cutoff. data_as_of: optional ingestion/version cutoff.

        Select the latest complete source scan covering EACH observation date;
        then apply vintage lifetimes and simulation availability. This also handles
        observations removed entirely from a later complete response.
        """
        from datetime import date

        if not ids:
            raise ValueError("Explicit series IDs required")
        self.series_catalog(ids)
        start = date.fromisoformat(start) if isinstance(start, str) else start
        end = date.fromisoformat(end) if isinstance(end, str) else end
        if start > end:
            raise ValueError("Invalid series query range")
        cutoff = utc(as_of)
        knowledge = (
            utc(data_as_of) if data_as_of is not None else datetime.now(timezone.utc)
        )
        return self.query(
            """WITH dates AS (
            SELECT DISTINCT series_id,observation_date FROM ingestion.series_observation
            WHERE series_id=ANY(%s) AND observation_date BETWEEN %s AND %s
        ), scans AS (
            SELECT d.*,s.run_id AS scan_run_id FROM dates d CROSS JOIN LATERAL (
                SELECT c.run_id FROM ingestion.series_coverage c WHERE c.series_id=d.series_id
                AND d.observation_date BETWEEN c.requested_start AND c.requested_end AND c.checked_at<=%s
                ORDER BY c.checked_at DESC,c.run_id DESC LIMIT 1
            ) s
        ), candidates AS (
            SELECT o.*, row_number() OVER (PARTITION BY o.series_id,o.observation_date ORDER BY o.vintage_date DESC) AS version_rank
            FROM scans s JOIN ingestion.series_run_observation m ON m.run_id=s.scan_run_id
            JOIN ingestion.series_observation o ON o.observation_id=m.observation_id
                AND o.series_id=s.series_id AND o.observation_date=s.observation_date
            WHERE o.available_at<=%s AND (o.available_to IS NULL OR %s<o.available_to)
        ) SELECT * FROM candidates WHERE version_rank=1 ORDER BY series_id,observation_date""",
            (ids, start, end, knowledge, cutoff, cutoff),
        )

    def series_status(self):
        return self.query("""SELECT c.series_id,c.name,c.frequency,c.unit,c.observation_start,c.observation_end,
            coverage.checked_at AS last_verified_scan,coverage.row_count,run.status AS last_run_status,
            observed.source_first_observation,observed.source_last_observation,observed.missing_vintage_rows,
            run.started_at AS last_attempt,
            coverage.checked_at IS NULL OR coverage.checked_at < now()-c.freshness_hours*interval '1 hour' AS stale_collection
            FROM ingestion.series_catalog c LEFT JOIN LATERAL (
                SELECT * FROM ingestion.series_coverage v WHERE v.series_id=c.series_id
                AND v.requested_start<=c.observation_start
                AND v.requested_end>=LEAST(COALESCE(c.observation_end,current_date),current_date)-1
                ORDER BY checked_at DESC LIMIT 1
            ) coverage ON true LEFT JOIN LATERAL (
                SELECT * FROM ingestion.dataset_run r WHERE r.dataset_id='fred_vintage' AND r.partition_key=c.series_id
                ORDER BY started_at DESC LIMIT 1
            ) run ON true LEFT JOIN LATERAL (
                SELECT min(o.observation_date) FILTER (WHERE o.status='valid') AS source_first_observation,
                    max(o.observation_date) FILTER (WHERE o.status='valid') AS source_last_observation,
                    count(*) FILTER (WHERE o.status='missing') AS missing_vintage_rows
                FROM ingestion.series_run_observation m JOIN ingestion.series_observation o USING(observation_id)
                WHERE m.run_id=coverage.run_id
            ) observed ON true WHERE c.active ORDER BY c.series_id""")
