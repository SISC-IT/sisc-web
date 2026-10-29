"""Repeatable SEC cohort collection and evidence reports."""

import json
from datetime import date
from pathlib import Path

from AI.orchestration.archive import RawArchive
from AI.orchestration.pit_store import PitStore
from AI.orchestration.sec import SecHttp, collect_sec
from AI.orchestration.sec_validation import DEFAULT_COHORT, audit_cohort, load_cohort

COMMANDS = {"sec-cohort-collect", "sec-validate", "sec-replay", "sec-feed-recover"}


def add_commands(sub):
    recover = sub.add_parser("sec-feed-recover")
    recover.add_argument("--file", type=Path, required=True)
    recover.add_argument("--source-url", required=True)
    recover.add_argument("--member", required=True)
    recover.add_argument("--archive", type=Path, required=True)
    replay = sub.add_parser("sec-replay")
    replay.add_argument("--run-id", required=True)
    replay.add_argument("--archive", type=Path, required=True)
    replay.add_argument("--isolate-unlinked-filings", action="store_true")
    collect = sub.add_parser("sec-cohort-collect")
    collect.add_argument("--cohort", type=Path, default=DEFAULT_COHORT)
    collect.add_argument("--archive", type=Path, required=True)
    collect.add_argument("--only-uncollected", action="store_true")
    audit = sub.add_parser("sec-validate")
    audit.add_argument("--cohort", type=Path, default=DEFAULT_COHORT)
    audit.add_argument("--archive", type=Path, required=True)
    audit.add_argument("--output", type=Path, required=True)
    audit.add_argument("--require-eight-years", action="store_true")
    audit.add_argument("--require-expected-years", action="store_true")


def execute(args, emit, code_sha):
    if args.command == "sec-feed-recover":
        from AI.orchestration.sec_feed import register_feed_submission

        result = register_feed_submission(
            PitStore(args.db),
            RawArchive(args.archive),
            args.file.read_bytes(),
            args.source_url,
            args.member,
            code_sha(),
        )
        emit(result)
        return int(result["status"] != "success")
    if args.command == "sec-replay":
        from AI.orchestration.sec_replay import replay_sec

        result = replay_sec(
            PitStore(args.db),
            args.run_id,
            args.archive,
            code_sha(),
            isolate_unlinked_filings=args.isolate_unlinked_filings,
        )
        emit(result)
        return int(result["status"] != "success")
    cohort = load_cohort(args.cohort)
    store = PitStore(args.db)
    if args.command == "sec-cohort-collect":
        from dotenv import load_dotenv

        load_dotenv(Path(__file__).parents[2] / ".env")
        client, archive, failures = SecHttp(), RawArchive(args.archive), 0
        targets = []
        for issuer in cohort["issuers"]:
            targets.append(issuer)
            targets.extend(
                {**p, "label": issuer["label"] + "-predecessor"}
                for p in issuer.get("predecessors", [])
            )
        for issuer in targets:
            # An explicit resume only skips a prior successful run that covered
            # this range (an unbounded successful scan is also sufficient).
            start, end = (
                date(issuer.get("start_year", cohort["start_year"]) - 1, 1, 1),
                date(issuer.get("end_year", cohort["end_year"]) + 1, 12, 31),
            )
            previous = store.query(
                """SELECT metadata FROM ingestion.dataset_run
                WHERE dataset_id='sec_companyfacts' AND partition_key=%s AND status='success'""",
                (issuer["cik"],),
            )
            covered = any(
                (
                    not r["metadata"].get("period_end_start")
                    or date.fromisoformat(r["metadata"]["period_end_start"]) <= start
                )
                and (
                    not r["metadata"].get("period_end_end")
                    or date.fromisoformat(r["metadata"]["period_end_end"]) >= end
                )
                for r in previous
            )
            if args.only_uncollected and covered:
                emit({"label": issuer["label"], "status": "already_collected"})
                continue
            result = collect_sec(
                store,
                archive,
                client,
                issuer["cik"],
                code_sha(),
                period_end_start=start,
                period_end_end=end,
            )
            emit({"label": issuer["label"], **result})
            failures += result["status"] != "success"
        return int(bool(failures))
    if args.output.exists():
        raise ValueError(
            "Validation output already exists; choose a new evidence report"
        )

    def progress(result):
        emit(
            {
                k: result[k]
                for k in (
                    "label",
                    "fact_count",
                    "source_failures",
                    "missing_annual_years",
                    "audit_error",
                )
                if k in result
            }
        )

    report = audit_cohort(store, cohort, args.archive, progress=progress)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    emit(
        {
            k: report[k]
            for k in (
                "issuer_count",
                "technical_failure_issuers",
                "complete_annual_coverage_issuers",
                "complete_expected_annual_coverage_issuers",
                "fact_rows",
                "boundary_checks",
                "report_hash",
                "golden_certification",
            )
        }
    )
    return int(
        bool(
            report["technical_failure_issuers"]
            or (
                args.require_expected_years
                and report["complete_expected_annual_coverage_issuers"]
                != report["issuer_count"]
            )
            or (
                args.require_eight_years
                and report["complete_annual_coverage_issuers"] != report["issuer_count"]
            )
        )
    )
