"""Typed Silver storage. Historical corrections never overwrite source facts."""

import json
import uuid

from AI.orchestration.metrics import METRIC_VERSIONS, METRICS
from AI.orchestration.store import Store


class PitStore(Store):
    def register_issuer(self, cik):
        from AI.orchestration.sec import cik_string

        cik = cik_string(cik)
        with self.transaction() as cur:
            cur.execute(
                "INSERT INTO ingestion.sec_issuer(cik) VALUES (%s) ON CONFLICT DO NOTHING",
                (cik,),
            )

    def commit_sec(
        self,
        cik,
        filings,
        facts,
        run_id,
        *,
        advance_watermark=True,
        isolated_rejections=None,
    ):
        if isolated_rejections and any(
            r.get("reason") != "missing_accepted_filing" for r in isolated_rejections
        ):
            raise ValueError("Only unlinked filings can be isolated")
        with self.transaction() as cur:
            cur.execute(
                "INSERT INTO ingestion.sec_issuer(cik) VALUES (%s) ON CONFLICT DO NOTHING",
                (cik,),
            )
            for metric, (kind, unit, tags) in METRICS.items():
                cur.execute(
                    """INSERT INTO ingestion.metric_catalog(metric_id,kind,unit,definition_version,tags)
                    VALUES (%s,%s,%s,%s,%s::jsonb) ON CONFLICT DO NOTHING""",
                    (metric, kind, unit, METRIC_VERSIONS[metric], json.dumps(tags)),
                )
            for filing in filings:
                cur.execute(
                    "SELECT accepted_at,available_at,cik,availability_basis FROM ingestion.filing WHERE accession_number=%s",
                    (filing["accession_number"],),
                )
                old = cur.fetchone()
                basis = filing.get("availability_basis", "accepted_plus_delay")
                if old and old != (
                    filing["accepted_at"],
                    filing["available_at"],
                    cik,
                    basis,
                ):
                    raise ValueError("Existing SEC filing timestamp/policy conflict")
                fields = [
                    "filing_id",
                    "cik",
                    "accession_number",
                    "form_type",
                    "period_end",
                    "filed_date",
                    "accepted_at",
                    "available_at",
                    "is_amendment",
                    "primary_document",
                    "raw_id",
                    "availability_basis",
                ]
                cur.execute(
                    f"""INSERT INTO ingestion.filing ({",".join(fields)},run_id)
                    VALUES ({",".join(["%s"] * (len(fields) + 1))}) ON CONFLICT DO NOTHING""",
                    tuple(basis if k == "availability_basis" else filing[k] for k in fields)
                    + (run_id,),
                )
            inserted = 0
            # Compare existing immutable natural keys in one query, not one round trip/fact.
            cur.execute(
                "SELECT source_key,value,available_at FROM ingestion.fundamental_fact WHERE cik=%s",
                (cik,),
            )
            old = {key: (value, available) for key, value, available in cur.fetchall()}
            fields = [
                "fact_id",
                "cik",
                "metric_id",
                "filing_id",
                "source_tag",
                "tag_priority",
                "period_start",
                "period_end",
                "reported_fy",
                "reported_fp",
                "value",
                "unit",
                "published_at",
                "available_at",
                "definition_version",
                "source_key",
                "raw_id",
            ]
            values = []
            for fact in facts:
                existing = old.get(fact["source_key"])
                if existing and existing != (fact["value"], fact["available_at"]):
                    raise ValueError("SEC natural key changed; remediation required")
                if not existing:
                    values.append(tuple(fact[k] for k in fields) + (run_id,))
            if values:
                from psycopg2.extras import execute_values

                result = execute_values(
                    cur,
                    f"""INSERT INTO ingestion.fundamental_fact
                    ({",".join(fields)},run_id) VALUES %s ON CONFLICT DO NOTHING RETURNING fact_id""",
                    values,
                    fetch=True,
                )
                inserted = len(result)
            cur.execute(
                """UPDATE ingestion.dataset_run SET status='success', finished_at=now(), heartbeat_at=now(),
                fetched_count=%s, inserted_count=%s WHERE run_id=%s""",
                (len(facts), inserted, run_id),
            )
            # Companyfacts is a complete source snapshot, not a daily session series.
            if isolated_rejections:
                cur.execute(
                    "INSERT INTO ingestion.quarantine(run_id,raw_id,reasons) VALUES (%s,%s,%s::jsonb)",
                    (run_id, facts[0]["raw_id"], json.dumps(isolated_rejections)),
                )
                cur.execute(
                    """UPDATE ingestion.dataset_run SET status='quarantined',rejected_count=%s,
                    metadata=metadata || '{"partial_commit":true}'::jsonb WHERE run_id=%s""",
                    (len(isolated_rejections), run_id),
                )
                return inserted
            if not advance_watermark:
                return inserted
            through = max(f["accepted_at"] or f["available_at"] for f in filings)
            cur.execute(
                """INSERT INTO ingestion.dataset_watermark
                (dataset_id,partition_key,last_success_event_time,last_success_run_id)
                VALUES ('sec_companyfacts',%s,%s,%s) ON CONFLICT (dataset_id,partition_key) DO UPDATE SET
                last_success_event_time=GREATEST(ingestion.dataset_watermark.last_success_event_time,excluded.last_success_event_time),
                last_success_run_id=excluded.last_success_run_id, updated_at=now()""",
                (cik, through, run_id),
            )
        return inserted

    def commit_reference(self, instruments, records, run_id, raw_id):
        with self.transaction() as cur:
            for instrument in instruments:
                keys = [
                    "instrument_id",
                    "name",
                    "asset_type",
                    "currency",
                    "exchange",
                    "timezone",
                ]
                cur.execute(
                    "SELECT name,asset_type,currency,exchange,timezone FROM ingestion.instrument WHERE instrument_id=%s",
                    (instrument["instrument_id"],),
                )
                old = cur.fetchone()
                if old and old != tuple(instrument[k] for k in keys[1:]):
                    raise ValueError("Instrument identity is immutable")
                cur.execute(
                    f"""INSERT INTO ingestion.instrument ({",".join(keys)}) VALUES (%s,%s,%s,%s,%s,%s)
                    ON CONFLICT DO NOTHING""",
                    tuple(instrument[k] for k in keys),
                )
            inserted = 0
            for record in records:
                keys = [
                    "kind",
                    "instrument_id",
                    "source",
                    "source_key",
                    "published_at",
                    "available_at",
                    "valid_from",
                    "valid_to",
                    "payload",
                    "content_hash",
                ]
                cur.execute(
                    """SELECT content_hash FROM ingestion.reference_record
                    WHERE kind=%s AND source=%s AND source_key=%s AND available_at=%s""",
                    (
                        record["kind"],
                        record["source"],
                        record["source_key"],
                        record["available_at"],
                    ),
                )
                old = cur.fetchone()
                if old and old[0] != record["content_hash"]:
                    raise ValueError(
                        "Conflicting reference revision at same availability time"
                    )
                cur.execute(
                    f"""INSERT INTO ingestion.reference_record
                    (record_id,{",".join(keys)},run_id,raw_id) VALUES ({",".join(["%s"] * 13)}) ON CONFLICT DO NOTHING""",
                    (str(uuid.uuid4()),)
                    + tuple(
                        json.dumps(record[k]) if k == "payload" else record[k]
                        for k in keys
                    )
                    + (run_id, raw_id),
                )
                inserted += cur.rowcount
            cur.execute(
                """UPDATE ingestion.dataset_run SET status='success', finished_at=now(), heartbeat_at=now(),
                fetched_count=%s, inserted_count=%s WHERE run_id=%s""",
                (len(records), inserted, run_id),
            )
        return inserted


class AsOfDataset:
    def __init__(self, store, *, data_as_of=None):
        from AI.orchestration.sec import utc

        self.store = store
        self.data_as_of = utc(data_as_of) if data_as_of is not None else None

    def fundamentals(self, cik, *, as_of, metrics=None):
        from AI.orchestration.sec import cik_string, utc

        cutoff = utc(as_of)
        # Filter BEFORE ranking, so a later amendment never erases the earlier visible fact.
        return self.store.query(
            """SELECT * FROM (
            SELECT f.*, p.availability_basis, row_number() OVER (
                PARTITION BY metric_id,f.period_start,f.period_end,unit
                ORDER BY f.available_at DESC,tag_priority ASC,source_key DESC) AS version_rank
            FROM ingestion.fundamental_fact f JOIN ingestion.filing p USING(filing_id)
            WHERE f.cik=%s AND f.available_at<=%s AND f.ingested_at<=COALESCE(%s,now()) AND (%s IS NULL OR metric_id=ANY(%s))
        ) visible WHERE version_rank=1 ORDER BY metric_id,period_end,period_start""",
            (cik_string(cik), cutoff, self.data_as_of, metrics, metrics),
        )

    def references(self, kind, *, as_of, instrument_id=None):
        from AI.orchestration.sec import utc

        if kind not in {"identifier", "membership", "corporate_action", "bar"}:
            raise ValueError("Unknown reference kind")
        # Apply instrument filter after revision selection; a correction may reassign identity.
        return self.store.query(
            """SELECT * FROM (
            SELECT r.*, row_number() OVER (PARTITION BY kind,source,source_key ORDER BY available_at DESC) AS version_rank
            FROM ingestion.reference_record r WHERE kind=%s AND available_at<=%s AND ingested_at<=COALESCE(%s,now())
        ) visible WHERE version_rank=1 AND (%s IS NULL OR instrument_id=%s)
        ORDER BY valid_from,source,source_key""",
            (kind, utc(as_of), self.data_as_of, instrument_id, instrument_id),
        )

    def resolve(self, identifier_type, value, *, on, as_of, venue=None):
        from datetime import date

        on = date.fromisoformat(on) if isinstance(on, str) else on
        rows = [
            r
            for r in self.references("identifier", as_of=as_of)
            if r["payload"]["identifier_type"] == identifier_type
            and r["payload"]["value"] == value
            and (venue is None or r["payload"].get("venue") == venue)
            and r["valid_from"] <= on
            and (r["valid_to"] is None or on < r["valid_to"])
        ]
        ids = {str(r["instrument_id"]) for r in rows}
        if len(ids) > 1:
            raise ValueError(
                "Ambiguous identifier; specify venue or repair overlapping histories"
            )
        return next(iter(ids)) if ids else None

    def universe(self, universe_id, *, on, as_of):
        from datetime import date

        on = date.fromisoformat(on) if isinstance(on, str) else on
        return sorted(
            {
                str(r["instrument_id"])
                for r in self.references("membership", as_of=as_of)
                if r["payload"]["universe_id"] == universe_id
                and r["valid_from"] <= on
                and (r["valid_to"] is None or on < r["valid_to"])
            }
        )

    def bars(self, instrument_id, *, start, end, basis, as_of):
        from datetime import date

        from AI.orchestration.reference import price_basis

        start = date.fromisoformat(start) if isinstance(start, str) else start
        end = date.fromisoformat(end) if isinstance(end, str) else end
        if start > end:
            raise ValueError("Invalid bar range")
        bars = [
            r
            for r in self.references("bar", as_of=as_of, instrument_id=instrument_id)
            if start <= r["valid_from"] <= end
        ]
        if len({r["valid_from"] for r in bars}) != len(bars):
            raise ValueError("Multiple price sources for session; reconcile before use")
        actions = self.references(
            "corporate_action", as_of=as_of, instrument_id=instrument_id
        )
        return price_basis(bars, actions, basis)

    def instrument_fundamentals(self, instrument_id, *, on, as_of, metrics=None):
        from datetime import date

        on = date.fromisoformat(on) if isinstance(on, str) else on
        identifiers = [
            r
            for r in self.references(
                "identifier", as_of=as_of, instrument_id=instrument_id
            )
            if r["payload"]["identifier_type"] == "cik"
            and r["valid_from"] <= on
            and (r["valid_to"] is None or on < r["valid_to"])
        ]
        ciks = {r["payload"]["value"] for r in identifiers}
        if len(ciks) != 1:
            raise ValueError("Exactly one visible CIK mapping required")
        return self.fundamentals(next(iter(ciks)), as_of=as_of, metrics=metrics)
