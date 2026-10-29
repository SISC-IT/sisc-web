"""Run from repo root: python -m AI.tests.ingestion.server_smoke --help.

Never tests against service tables. Creates a random database on the configured
server, verifies its identity before resets, and removes only that database.
Credentials stay in memory/environment, never in the report or command line.
"""

import argparse
import hashlib
import io
import json
import os
import uuid
from contextlib import closing
from pathlib import Path

import psycopg2
from psycopg2 import sql

from AI.libs.database.connection import _load_db_env, _normalize_prefix, get_db_conn
from AI.orchestration.pit_store import PitStore
from AI.orchestration.sec_validation import audit_cohort, load_cohort


def fingerprint(conn):
    with conn.cursor() as cur:
        cur.execute("""SELECT table_schema,table_name,column_name,data_type
            FROM information_schema.columns WHERE table_schema IN ('public','ingestion')
            ORDER BY 1,2,3""")
        return hashlib.sha256(json.dumps(cur.fetchall()).encode()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-prefix", default="DB")
    parser.add_argument("--source-dsn-env", default="INGESTION_SMOKE_SOURCE_DSN")
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Choose a new report path")
    source_dsn = os.environ[args.source_dsn_env]
    admin = get_db_conn(args.server_prefix)
    admin.autocommit = True
    before = fingerprint(admin)
    params = admin.get_dsn_parameters()
    name = "ingestion_test_codex_" + uuid.uuid4().hex[:16]
    assert params["dbname"] != name
    with admin.cursor() as cur:
        cur.execute("SHOW server_version")
        version = cur.fetchone()[0]
        cur.execute(
            sql.SQL("CREATE DATABASE {} TEMPLATE template0").format(
                sql.Identifier(name)
            )
        )
    params["dbname"] = name
    params["password"] = _load_db_env(_normalize_prefix(args.server_prefix))["password"]
    dsn = psycopg2.extensions.make_dsn(**params)
    report = {
        "server_version": version,
        "test_database": name,
        "production_schema_before": before,
    }
    try:
        # Restore a real SEC sample into the disposable server database using
        # COPY. Local pytest already covers synthetic edge cases; this smoke
        # test exercises migrations, constraints, PIT queries and evidence on
        # the configured PostgreSQL server with a bounded number of connections.
        with (
            closing(psycopg2.connect(dsn)) as destination,
            closing(psycopg2.connect(source_dsn)) as source,
            destination,
            source,
        ):
            with destination.cursor() as cur:
                cur.execute("SELECT current_database()")
                if cur.fetchone()[0] != name:
                    raise ValueError("Disposable database identity mismatch")
                cur.execute("DROP SCHEMA IF EXISTS ingestion CASCADE")
                for path in sorted(Path("AI/migrations").glob("[0-9][0-9][0-9]_*.sql")):
                    cur.execute(path.read_text(encoding="utf-8"))
                selected = (
                    "0000789019",
                    "0001018724",
                    "0000773840",
                    "0001108524",
                    "0000093410",
                    "0001551152",
                )
                for table in [
                    "dataset_run",
                    "raw_object",
                    "sec_issuer",
                    "metric_catalog",
                    "filing",
                    "fundamental_fact",
                ]:
                    buffer = io.StringIO()
                    with source.cursor() as source_cur:
                        query = sql.SQL("SELECT * FROM ingestion.{}").format(
                            sql.Identifier(table)
                        )
                        if table in {"sec_issuer", "filing", "fundamental_fact"}:
                            query += sql.SQL(" WHERE cik IN {} ").format(
                                sql.Literal(selected)
                            )
                        source_cur.copy_expert(
                            sql.SQL("COPY ({}) TO STDOUT").format(query), buffer
                        )
                    buffer.seek(0)
                    cur.copy_expert(
                        sql.SQL("COPY ingestion.{} FROM STDIN").format(
                            sql.Identifier(table)
                        ),
                        buffer,
                    )
        config = load_cohort()
        config["issuers"] = [i for i in config["issuers"] if i["cik"] in selected]
        sample = audit_cohort(
            PitStore(connection_factory=lambda: psycopg2.connect(dsn)),
            config,
            args.archive,
            progress=lambda r: print("Server evidence:", r["label"], flush=True),
        )
        report["sec_sample"] = sample
        with closing(psycopg2.connect(dsn)) as checked:
            with checked.cursor() as cur:
                cur.execute(
                    "SELECT version FROM ingestion.schema_version ORDER BY version"
                )
                report["schema_versions"] = [row[0] for row in cur.fetchall()]
                cur.execute("SELECT count(*) FROM ingestion.fundamental_fact")
                report["sample_fact_rows"] = cur.fetchone()[0]
                cur.execute("SELECT clock_timestamp()")
                report["server_clock_observed"] = cur.fetchone()[0].isoformat()
        report["passed"] = (
            sample["technical_failure_issuers"] == 0
            and sample["complete_expected_annual_coverage_issuers"] == len(selected)
            and report["schema_versions"] == [1, 2, 3, 4, 5]
            and report["sample_fact_rows"] > 0
        )
    finally:
        with admin.cursor() as cur:
            cur.execute(
                "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname=%s AND pid<>pg_backend_pid()",
                (name,),
            )
            cur.execute(sql.SQL("DROP DATABASE {}").format(sql.Identifier(name)))
        report["test_database_removed"] = True
        report["production_schema_after"] = fingerprint(admin)
        report["production_schema_unchanged"] = (
            report["production_schema_after"] == before
        )
        admin.close()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
    print(
        json.dumps(
            {
                k: report[k]
                for k in [
                    "passed",
                    "schema_versions",
                    "sample_fact_rows",
                    "test_database_removed",
                    "production_schema_unchanged",
                ]
            }
        ),
        flush=True,
    )
    return int(not report["passed"])


if __name__ == "__main__":
    raise SystemExit(main())
