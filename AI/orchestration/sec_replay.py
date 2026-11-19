"""Reprocess checksum-verified SEC Bronze without fetching or rewriting originals."""

from datetime import date, datetime, timezone

from AI.orchestration.metrics import MAPPING_VERSION
from AI.orchestration.sec import cik_string, parse_facts, parse_filings
from AI.orchestration.sec_validation import Evidence


def replay_sec(
    store, source_run_id, archive_root, code_sha, *, isolate_unlinked_filings=False
):
    parent = store.query(
        "SELECT * FROM ingestion.dataset_run WHERE run_id=%s", (source_run_id,)
    )
    if not parent or parent[0]["dataset_id"] not in {"sec_companyfacts", "sec_replay"}:
        raise ValueError("A SEC source run is required")
    parent = parent[0]
    cik = cik_string(parent["partition_key"])
    with store.lock("sec_companyfacts", cik) as acquired:
        if not acquired:
            return {"cik": cik, "status": "locked"}
        with store.transaction() as cur:
            cur.execute(
                """UPDATE ingestion.dataset_run SET status='interrupted',finished_at=now(),
                error_class='LostReplayWorker' WHERE dataset_id='sec_replay' AND partition_key=%s
                AND status='running'""",
                (cik,),
            )
        config = {
            **parent["metadata"],
            "mapping": MAPPING_VERSION,
            "replay_of_run_id": str(source_run_id),
            "isolate_unlinked_filings": isolate_unlinked_filings,
        }
        run = store.start("sec_replay", cik, None, code_sha, config)
        try:
            # For replay-of-replay, original raw IDs remain linked to the root
            # source run. Resolve through explicit provenance, never refetch.
            root = parent
            seen = set()
            while root["metadata"].get("replay_of_run_id"):
                key = str(root["run_id"])
                if key in seen:
                    raise ValueError("Cyclic replay provenance")
                seen.add(key)
                root = store.query(
                    "SELECT * FROM ingestion.dataset_run WHERE run_id=%s",
                    (root["metadata"]["replay_of_run_id"],),
                )[0]
                if (
                    root["dataset_id"] not in {"sec_companyfacts", "sec_replay"}
                    or root["partition_key"] != cik
                ):
                    raise ValueError("Replay provenance identity mismatch")
            raws = store.query(
                """SELECT raw_id,request_metadata FROM ingestion.raw_object
                WHERE run_id=%s ORDER BY fetched_at""",
                (str(root["run_id"]),),
            )
            by_path = {
                r["request_metadata"]["path"]: r["raw_id"]
                for r in raws
                if r["request_metadata"].get("status") == 200
            }
            evidence = Evidence(store, archive_root)
            raw = by_path[f"submissions/CIK{cik}.json"]
            payload = evidence.load(raw)
            if cik_string(payload["cik"]) != cik:
                raise ValueError("Replay submissions CIK mismatch")
            now = datetime.now(timezone.utc)
            delay = config.get("delay_seconds", 60)
            filings, rejected = parse_filings(payload, cik, raw, now, delay)
            for history in payload["filings"].get("files", []):
                raw = by_path["submissions/" + history["name"]]
                more, errors = parse_filings(evidence.load(raw), cik, raw, now, delay)
                filings.extend(more)
                rejected.extend(errors)
            raw = by_path[f"api/xbrl/companyfacts/CIK{cik}.json"]
            from AI.orchestration.sec_feed import recovered_filings

            known = {f["accession_number"] for f in filings}
            filings.extend(
                f
                for f in recovered_filings(store, cik, archive_root)
                if f["accession_number"] not in known
            )
            facts, errors = parse_facts(
                evidence.load(raw),
                cik,
                filings,
                raw,
                period_end_start=date.fromisoformat(config["period_end_start"])
                if config.get("period_end_start")
                else None,
                period_end_end=date.fromisoformat(config["period_end_end"])
                if config.get("period_end_end")
                else None,
            )
            rejected.extend(errors)
            isolated = (
                rejected
                if isolate_unlinked_filings
                and facts
                and all(r.get("reason") == "missing_accepted_filing" for r in rejected)
                else []
            )
            if (rejected and not isolated) or not facts:
                store.quarantine(
                    run, raw, rejected or [{"reason": "empty_replay"}], len(facts)
                )
                return {
                    "cik": cik,
                    "run_id": run,
                    "status": "quarantined",
                    "rejected": len(rejected),
                }
            inserted = store.commit_sec(
                cik,
                filings,
                facts,
                run,
                advance_watermark=False,
                isolated_rejections=isolated,
            )
            return {
                "cik": cik,
                "run_id": run,
                "status": "quarantined" if isolated else "success",
                "isolated_rejections": len(isolated),
                "facts": len(facts),
                "inserted": inserted,
            }
        except (OSError, ValueError, KeyError, IndexError, TypeError, ArithmeticError):
            store.quarantine(
                run, None, [{"reason": "invalid_replay_source_or_history"}], 0
            )
            return {"cik": cik, "run_id": run, "status": "quarantined"}
        except Exception as exc:
            store.finish(run, "failed", type(exc).__name__)
            return {
                "cik": cik,
                "run_id": run,
                "status": "failed",
                "error_class": type(exc).__name__,
            }
