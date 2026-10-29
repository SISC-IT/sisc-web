"""Explicitly sourced reference imports; never infer historical membership from today's tickers."""

import json
import uuid
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from zoneinfo import ZoneInfo

from AI.orchestration.core import content_hash, validate_bars
from AI.orchestration.sec import cik_string, utc


def normalize_reference(document, now=None):
    now = now or datetime.now(timezone.utc)
    instruments, records = document.get("instruments", []), []
    for instrument in instruments:
        uuid.UUID(instrument["instrument_id"])
        if not all(
            instrument.get(k)
            for k in ["name", "asset_type", "currency", "exchange", "timezone"]
        ):
            raise ValueError("Incomplete instrument")
        if len(instrument["currency"]) != 3:
            raise ValueError("Invalid currency")
        ZoneInfo(instrument["timezone"])
    for item in document["records"]:
        r = dict(item)
        uuid.UUID(r["instrument_id"])
        r["published_at"], r["available_at"] = (
            utc(r["published_at"]),
            utc(r["available_at"]),
        )
        if r["available_at"] < r["published_at"] or r["available_at"] > now:
            raise ValueError("Invalid reference availability")
        r["valid_from"] = date.fromisoformat(r["valid_from"])
        r["valid_to"] = date.fromisoformat(r["valid_to"]) if r.get("valid_to") else None
        if r["valid_to"] and r["valid_to"] <= r["valid_from"]:
            raise ValueError("Invalid half-open validity range")
        if not r["source"] or not r["source_key"]:
            raise ValueError("Reference source and natural key required")
        p = r["payload"]
        if r["kind"] == "identifier":
            if p["identifier_type"] not in {"ticker", "cik", "isin"} or not p["value"]:
                raise ValueError("Invalid identifier")
            if p["identifier_type"] == "ticker" and not p.get("venue"):
                raise ValueError("Ticker venue required")
            if p["identifier_type"] == "cik" and cik_string(p["value"]) != p["value"]:
                raise ValueError("CIK must be zero-padded")
        elif r["kind"] == "membership":
            if not p["universe_id"]:
                raise ValueError("Universe ID required")
        elif r["kind"] == "corporate_action":
            if p["action_type"] == "split":
                values = [
                    Decimal(p[k]) for k in ["ratio_numerator", "ratio_denominator"]
                ]
                if any(not v.is_finite() or v <= 0 for v in values):
                    raise ValueError("Invalid split ratio")
            elif p["action_type"] == "cash_dividend":
                cash = Decimal(p["cash_amount"])
                if not cash.is_finite() or cash < 0 or not p.get("currency"):
                    raise ValueError("Invalid dividend")
                if p.get("share_basis") != "post_split":
                    raise ValueError(
                        "Dividend cash must specify post_split share basis"
                    )
            else:
                raise ValueError("Unsupported corporate action")
        elif r["kind"] == "bar":
            if p.get("price_basis") != "raw" or not p.get("currency"):
                raise ValueError("Verified raw price basis and currency required")
            if utc(p["session_close"]) > r["available_at"]:
                raise ValueError("Bar cannot be available before session close")
            if utc(p["session_close"]).date() not in {
                r["valid_from"],
                r["valid_from"] + timedelta(days=1),
            }:
                raise ValueError("Session close and session date mismatch")
            if validate_bars(
                [{"date": r["valid_from"].isoformat(), **p}], [r["valid_from"]]
            ):
                raise ValueError("Invalid reference bar")
        else:
            raise ValueError("Unknown reference kind")
        r["content_hash"] = content_hash(item)
        records.append(r)
    if not records:
        raise ValueError("Empty reference import")
    return instruments, records


def import_reference(store, archive, payload, code_sha):
    with store.lock("reference_import", "all") as acquired:
        if not acquired:
            return {"status": "locked"}
        run = store.start(
            "reference_import", "all", None, code_sha, {"definition": "reference_v1"}
        )
        raw_id = None
        try:
            checksum, uri = archive.put(payload)
            raw_id = store.raw(
                run,
                checksum,
                uri,
                datetime.now(timezone.utc),
                {},
                source="reference_import",
                format="json-v1",
            )
            instruments, records = normalize_reference(json.loads(payload))
            count = store.commit_reference(instruments, records, run, raw_id)
            return {"status": "success", "run_id": run, "inserted": count}
        except (ValueError, KeyError, TypeError, ArithmeticError):
            store.quarantine(
                run,
                raw_id,
                [{"reason": "invalid_reference_or_conflicting_revision"}],
                0,
            )
            return {"status": "quarantined", "run_id": run}
        except Exception as exc:
            store.finish(run, "failed", type(exc).__name__)
            return {
                "status": "failed",
                "run_id": run,
                "error_class": type(exc).__name__,
            }


def price_basis(bars, actions, basis):
    if basis not in {"raw", "split_adjusted", "total_return"}:
        raise ValueError("Explicit raw, split_adjusted or total_return basis required")
    bars = sorted(bars, key=lambda r: r["valid_from"])
    if not bars:
        return []
    end = bars[-1]["valid_from"]
    if len({b["payload"]["currency"] for b in bars}) != 1:
        raise ValueError("Currency changes require an explicit conversion policy")
    keys = [(a["valid_from"], a["payload"]["action_type"]) for a in actions]
    if len(set(keys)) != len(keys):
        raise ValueError("Multiple action sources; reconcile before adjustment")
    result, previous, index = [], None, Decimal(100)
    for bar in bars:
        day, p = bar["valid_from"], bar["payload"]
        value = {
            k: Decimal(str(p[k])) for k in ["open", "high", "low", "close", "volume"]
        }
        used = [bar]
        if basis == "split_adjusted":
            factor = Decimal(1)
            for action in actions:
                if (
                    day < action["valid_from"] <= end
                    and action["payload"]["action_type"] == "split"
                ):
                    factor *= Decimal(action["payload"]["ratio_numerator"]) / Decimal(
                        action["payload"]["ratio_denominator"]
                    )
                    used.append(action)
            value = {
                k: v * factor if k == "volume" else v / factor for k, v in value.items()
            }
        elif basis == "total_return":
            if previous:
                split, cash = Decimal(1), Decimal(0)
                for action in actions:
                    if previous["valid_from"] < action["valid_from"] <= day:
                        # Missing ex-date bars cannot support exact close reinvestment.
                        if action["valid_from"] != day:
                            raise ValueError("Missing corporate-action session bar")
                        a = action["payload"]
                        if a["action_type"] == "split":
                            split *= Decimal(a["ratio_numerator"]) / Decimal(
                                a["ratio_denominator"]
                            )
                        else:
                            if a["currency"] != p["currency"]:
                                raise ValueError("Dividend currency mismatch")
                            cash += Decimal(a["cash_amount"])
                        used.append(action)
                index *= (
                    (value["close"] + cash)
                    * split
                    / Decimal(previous["payload"]["close"])
                )
            value = {"total_return_index": index}
            # Accumulated index depends on ALL previous bars/actions, not just today's inputs.
            if result:
                used.append(
                    {"available_at": result[-1]["available_at"], "record_id": None}
                )
        result.append(
            {
                "session_date": day,
                "basis": basis,
                **value,
                "available_at": max(r["available_at"] for r in used),
                "source_record_ids": sorted(
                    {str(r["record_id"]) for r in used if r.get("record_id")}
                    | (
                        set(result[-1]["source_record_ids"])
                        if basis == "total_return" and result
                        else set()
                    )
                ),
            }
        )
        previous = bar
    return result
