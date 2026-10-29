from __future__ import annotations

# ruff: noqa: E402

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.append(str(PROJECT_ROOT))

from AI.modules.data_collector.components.news.event_linker import (
    EventNewsRepository,
)


def _parse_datetime(value: str | None) -> datetime | None:
    if value is None:
        return None
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    parsed = datetime.fromisoformat(normalized)
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError("--updated-after must include a timezone")
    return parsed


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="SEC events and stored company news reconciliation",
    )
    parser.add_argument("--db-name", default="db")
    parser.add_argument("--event-id", help="reconcile one stored SEC event")
    parser.add_argument(
        "--sync-sec-filings",
        action="store_true",
        help="import sec_filings/sec_filing_documents before reconciliation",
    )
    parser.add_argument(
        "--updated-after",
        help="only import SEC filings updated after this ISO-8601 timestamp",
    )
    parser.add_argument("--limit", type=int, default=100)
    return parser.parse_args(argv)


def run(args: argparse.Namespace) -> dict:
    repository = EventNewsRepository(args.db_name)
    if args.event_id:
        event = repository.get_event(args.event_id)
        if event is None:
            raise ValueError(f"unknown event_id: {args.event_id}")
        links = repository.reconcile_event(event)
        return {"event_count": 1, "link_count": len(links)}

    if args.sync_sec_filings:
        events = repository.sync_from_sec_filings(
            limit=args.limit,
            updated_after=_parse_datetime(args.updated_after),
        )
        link_count = sum(len(repository.reconcile_event(event)) for event in events)
        return {
            "synced_event_count": len(events),
            "event_count": len(events),
            "link_count": link_count,
        }

    return repository.reconcile_due_events(limit=args.limit)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        payload = {"status": "success", **run(args)}
        exit_code = 0
    except Exception as exc:
        payload = {
            "status": "failed",
            "failure_reason": f"{type(exc).__name__}: {exc}",
        }
        exit_code = 1
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
