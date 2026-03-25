"""
Trade-log validator + CSV exporter.

How to use:
  python AI/tests/export_trade_logs.py

This script does not use CLI args.
Edit USER SETTINGS below if you want filters.
"""

from __future__ import annotations

import itertools
import math
import os
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

import pandas as pd

current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn


# =========================
# USER SETTINGS
# =========================
DB_NAME = "db"
RUN_ID: str | None = None
START_DATE: str | None = None  # e.g. "2026-03-01"
END_DATE: str | None = None    # e.g. "2026-03-24"
FETCH_LIMIT: int | None = None  # None = no limit

TOLERANCE = Decimal("0.02")
ALLOW_SHORT = False
SKIP_CASH_CHECKS = False
MAX_DAY_PERMUTATIONS = 40320
SHOW_TOP_FINDINGS = 40

OUTPUT_PATH: Path | None = None  # None -> AI/tests/out/trade_logs_YYYYMMDD_HHMMSS.csv


BUY = "BUY"
SELL = "SELL"
TWO_DP = Decimal("0.01")


@dataclass
class ValidationIssue:
    severity: str  # ERROR | WARN
    code: str
    message: str
    row_no: int | None = None
    row_id: int | None = None

    def as_text(self) -> str:
        row_hint = ""
        if self.row_no is not None:
            row_hint = f"row={self.row_no}"
            if self.row_id is not None:
                row_hint += f", id={self.row_id}"
            row_hint = f" [{row_hint}]"
        return f"{self.severity} {self.code}{row_hint} - {self.message}"


def _to_decimal(value: object) -> Decimal:
    if value is None:
        return Decimal("0")
    return Decimal(str(value))


def _q2(value: Decimal) -> Decimal:
    return value.quantize(TWO_DP, rounding=ROUND_HALF_UP)


def _apply_trade(cash_before: Decimal, row: dict) -> Decimal:
    value = _to_decimal(row["value"])
    commission = _to_decimal(row["commission"])
    if row["side"] == BUY:
        return _q2(cash_before - value - commission)
    return _q2(cash_before + value - commission)


def _infer_pre_trade_cash(row: dict) -> Decimal:
    cash_after = _to_decimal(row["cash_after"])
    value = _to_decimal(row["value"])
    commission = _to_decimal(row["commission"])
    if row["side"] == BUY:
        return _q2(cash_after + value + commission)
    return _q2(cash_after - value + commission)


def fetch_trade_logs(
    db_name: str,
    run_id: str | None = None,
    start_date: str | None = None,
    end_date: str | None = None,
    limit: int | None = None,
) -> list[dict]:
    clauses: list[str] = []
    params: list[object] = []

    if run_id:
        clauses.append("run_id = %s")
        params.append(run_id)
    if start_date:
        clauses.append("fill_date >= %s")
        params.append(start_date)
    if end_date:
        clauses.append("fill_date <= %s")
        params.append(end_date)

    where_sql = f"WHERE {' AND '.join(clauses)}" if clauses else ""
    limit_sql = ""
    if limit is not None and limit > 0:
        limit_sql = "LIMIT %s"
        params.append(limit)

    query = f"""
        SELECT
            id,
            run_id,
            ticker,
            side,
            signal,
            qty,
            fill_price,
            value,
            commission,
            cash_after,
            position_qty,
            pnl_realized,
            pnl_unrealized,
            signal_date,
            fill_date,
            created_at
        FROM public.executions
        {where_sql}
        ORDER BY fill_date ASC, created_at ASC, id ASC
        {limit_sql}
    """

    conn = get_db_conn(db_name)
    if conn is None:
        raise RuntimeError(f"DB connection failed for db name '{db_name}'.")

    try:
        with conn.cursor() as cursor:
            cursor.execute(query, tuple(params))
            columns = [col[0] for col in cursor.description]
            rows = [dict(zip(columns, values)) for values in cursor.fetchall()]
        for idx, row in enumerate(rows, start=1):
            row["_row_no"] = idx
        return rows
    finally:
        conn.close()


def build_default_output_path() -> Path:
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    return Path("AI/tests/out") / f"trade_logs_{ts}.csv"


def save_csv(rows: list[dict], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    export_rows = [{k: v for k, v in row.items() if not k.startswith("_")} for row in rows]
    pd.DataFrame(export_rows).to_csv(output_path, index=False, encoding="utf-8-sig")


def _try_day_permutation(
    day_rows: list[dict], tolerance: Decimal, max_permutations: int
) -> tuple[str, tuple[int, ...] | None, Decimal | None, Decimal | None, int]:
    if len(day_rows) <= 1:
        start_cash = _infer_pre_trade_cash(day_rows[0])
        end_cash = _apply_trade(start_cash, day_rows[0])
        return "feasible", (0,), start_cash, end_cash, 1

    count = math.factorial(len(day_rows))
    if count > max_permutations:
        return "capped", None, None, None, count

    for perm in itertools.permutations(range(len(day_rows))):
        cash = _infer_pre_trade_cash(day_rows[perm[0]])
        valid = True
        for pos in perm:
            row = day_rows[pos]
            cash = _apply_trade(cash, row)
            if abs(cash - _to_decimal(row["cash_after"])) > tolerance:
                valid = False
                break
        if valid:
            return "feasible", perm, _infer_pre_trade_cash(day_rows[perm[0]]), cash, count
    return "infeasible", None, None, None, count


def validate_trade_logs(
    rows: list[dict],
    tolerance: Decimal = Decimal("0.02"),
    allow_short: bool = False,
    skip_cash_checks: bool = False,
    max_day_permutations: int = 40320,
) -> list[ValidationIssue]:
    issues: list[ValidationIssue] = []
    if not rows:
        return issues

    for row in rows:
        row_no = row["_row_no"]
        row_id = int(row["id"]) if row["id"] is not None else None
        side = str(row["side"])
        signal = str(row["signal"])
        qty = _to_decimal(row["qty"])
        fill_price = _to_decimal(row["fill_price"])
        value = _to_decimal(row["value"])
        pnl_realized = _to_decimal(row["pnl_realized"])
        pnl_unrealized = _to_decimal(row["pnl_unrealized"])
        position_qty = _to_decimal(row.get("position_qty"))

        if side not in {BUY, SELL}:
            issues.append(
                ValidationIssue("ERROR", "invalid_side", f"Unknown side '{side}'.", row_no, row_id)
            )
        if side != signal:
            issues.append(
                ValidationIssue("ERROR", "side_signal_mismatch", f"side={side}, signal={signal}", row_no, row_id)
            )
        if qty <= 0:
            issues.append(ValidationIssue("ERROR", "non_positive_qty", f"qty={qty}", row_no, row_id))
        if fill_price <= 0:
            issues.append(
                ValidationIssue("ERROR", "non_positive_fill_price", f"fill_price={fill_price}", row_no, row_id)
            )

        expected_value = _q2(qty * fill_price)
        if abs(expected_value - value) > tolerance:
            issues.append(
                ValidationIssue(
                    "ERROR",
                    "value_mismatch",
                    f"qty*fill_price={expected_value}, value={_q2(value)}",
                    row_no,
                    row_id,
                )
            )

        if row["fill_date"] is not None and row["signal_date"] is not None and row["fill_date"] < row["signal_date"]:
            issues.append(
                ValidationIssue(
                    "ERROR",
                    "fill_before_signal",
                    f"signal_date={row['signal_date']}, fill_date={row['fill_date']}",
                    row_no,
                    row_id,
                )
            )

        run_id = str(row["run_id"]) if row["run_id"] is not None else ""
        if run_id.startswith("daily_"):
            run_date = run_id.replace("daily_", "", 1)
            if str(row["signal_date"]) != run_date:
                issues.append(
                    ValidationIssue(
                        "WARN",
                        "runid_signal_date_mismatch",
                        f"run_id={run_id}, signal_date={row['signal_date']}",
                        row_no,
                        row_id,
                    )
                )

        # In long-only mode, BUY should not realize PnL.
        # In short-allowed mode, BUY can be a short-cover and may realize PnL.
        if side == BUY and (not allow_short) and abs(pnl_realized) > tolerance:
            issues.append(
                ValidationIssue("ERROR", "buy_with_realized_pnl", f"pnl_realized={_q2(pnl_realized)}", row_no, row_id)
            )

        # Unrealized PnL is expected to be ~0 only when position is fully closed.
        if side == SELL and position_qty == 0 and abs(pnl_unrealized) > tolerance:
            issues.append(
                ValidationIssue(
                    "ERROR", "sell_with_unrealized_pnl_closed_position", f"pnl_unrealized={_q2(pnl_unrealized)}", row_no, row_id
                )
            )
        if _to_decimal(row["cash_after"]) < 0:
            issues.append(
                ValidationIssue("WARN", "negative_cash_after", f"cash_after={_q2(_to_decimal(row['cash_after']))}", row_no, row_id)
            )

    dup_cols = [
        "run_id",
        "ticker",
        "side",
        "signal",
        "qty",
        "fill_price",
        "value",
        "commission",
        "cash_after",
        "pnl_realized",
        "pnl_unrealized",
        "signal_date",
        "fill_date",
    ]
    duplicates: dict[tuple, list[int]] = defaultdict(list)
    for row in rows:
        key = tuple(str(row[col]) for col in dup_cols)
        duplicates[key].append(int(row["id"]))
    for dup_ids in duplicates.values():
        if len(dup_ids) > 1:
            issues.append(ValidationIssue("ERROR", "exact_duplicate_rows", f"duplicate execution ids={dup_ids}"))

    qty_by_ticker: dict[str, Decimal] = defaultdict(lambda: Decimal("0"))
    avg_price_by_ticker: dict[str, Decimal] = defaultdict(lambda: Decimal("0"))
    for row in rows:
        row_no = row["_row_no"]
        row_id = int(row["id"])
        ticker = str(row["ticker"])
        side = str(row["side"])
        qty = _to_decimal(row["qty"])
        price = _to_decimal(row["fill_price"])
        commission = _to_decimal(row["commission"])
        logged_realized = _q2(_to_decimal(row["pnl_realized"]))
        prev_qty = qty_by_ticker[ticker]
        prev_avg = avg_price_by_ticker[ticker]

        if side == BUY:
            new_qty = prev_qty + qty
            if new_qty > 0:
                total_cost = (prev_qty * prev_avg) + (qty * price)
                avg_price_by_ticker[ticker] = total_cost / new_qty
            qty_by_ticker[ticker] = new_qty
            continue

        if not allow_short and qty > prev_qty:
            issues.append(
                ValidationIssue(
                    "ERROR",
                    "oversell_detected",
                    f"{ticker}: sell={qty}, position_before={prev_qty}",
                    row_no,
                    row_id,
                )
            )
            continue

        sell_qty = qty if allow_short else min(qty, prev_qty)
        expected_realized = _q2(((price - prev_avg) * sell_qty) - commission)
        if abs(expected_realized - logged_realized) > tolerance:
            issues.append(
                ValidationIssue(
                    "ERROR",
                    "realized_pnl_mismatch",
                    f"{ticker}: expected={expected_realized}, logged={logged_realized}",
                    row_no,
                    row_id,
                )
            )

        new_qty = prev_qty - qty
        qty_by_ticker[ticker] = new_qty
        if new_qty <= 0:
            avg_price_by_ticker[ticker] = Decimal("0")

    if skip_cash_checks:
        return issues

    by_day: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        by_day[str(row["fill_date"])].append(row)

    prev_day_end: Decimal | None = None
    for day in sorted(by_day.keys()):
        day_rows = by_day[day]
        first_cash = _infer_pre_trade_cash(day_rows[0])
        cash = first_cash
        strict_ok = True
        for row in day_rows:
            cash = _apply_trade(cash, row)
            if abs(cash - _to_decimal(row["cash_after"])) > tolerance:
                strict_ok = False
                break

        if not strict_ok:
            status, perm, day_start, day_end, perm_count = _try_day_permutation(
                day_rows, tolerance, max_day_permutations
            )
            if status == "capped":
                issues.append(
                    ValidationIssue(
                        "WARN",
                        "day_cash_unverified_permutation_cap",
                        f"{day}: skipped exhaustive search ({perm_count} permutations > cap {max_day_permutations}).",
                    )
                )
                prev_day_end = None
                continue
            if status == "infeasible":
                issues.append(
                    ValidationIssue(
                        "ERROR",
                        "day_cash_infeasible",
                        f"{day}: no feasible intraday cash path found after checking {perm_count} permutations.",
                    )
                )
                prev_day_end = None
                continue

            ordered_ids = [int(day_rows[i]["id"]) for i in perm or ()]
            issues.append(
                ValidationIssue(
                    "WARN",
                    "day_order_mismatch",
                    f"{day}: current row order is not cash-consistent; feasible id order={ordered_ids}",
                )
            )
        else:
            day_start, day_end = first_cash, cash

        if day_start is None or day_end is None:
            prev_day_end = None
            continue

        if prev_day_end is not None:
            gap = _q2(day_start - prev_day_end)
            if abs(gap) > tolerance:
                issues.append(
                    ValidationIssue(
                        "WARN",
                        "cross_day_cash_gap",
                        f"{day}: start={day_start}, prev_day_end={prev_day_end}, gap={gap}",
                    )
                )
        prev_day_end = day_end

    return issues


def _date_range_text(rows: list[dict]) -> str:
    if not rows:
        return "-"
    return f"{rows[0]['fill_date']} -> {rows[-1]['fill_date']}"


def print_report(rows: list[dict], issues: list[ValidationIssue], show: int = 40) -> tuple[int, int]:
    errors = [x for x in issues if x.severity == "ERROR"]
    warns = [x for x in issues if x.severity == "WARN"]

    print(f"[INFO] Rows fetched: {len(rows)}")
    print(f"[INFO] Fill-date range: {_date_range_text(rows)}")
    print(f"[INFO] Findings: ERROR={len(errors)}, WARN={len(warns)}")

    if not issues:
        print("[PASS] No logical contradictions found for the configured checks.")
        return 0, 0

    counts = Counter((x.severity, x.code) for x in issues)
    print("\n[Summary by code]")
    for (severity, code), count in sorted(counts.items(), key=lambda item: (-item[1], item[0][0], item[0][1])):
        print(f" - {severity:5} {code:28} : {count}")

    print(f"\n[Top {min(max(show, 0), len(issues))} findings]")
    for issue in issues[: max(show, 0)]:
        print(f" - {issue.as_text()}")

    return len(errors), len(warns)


def main() -> None:
    rows = fetch_trade_logs(
        db_name=DB_NAME,
        run_id=RUN_ID,
        start_date=START_DATE,
        end_date=END_DATE,
        limit=FETCH_LIMIT,
    )

    output_path = OUTPUT_PATH if OUTPUT_PATH is not None else build_default_output_path()
    save_csv(rows=rows, output_path=output_path)
    print(f"[INFO] Raw rows exported: {output_path}")

    issues = validate_trade_logs(
        rows=rows,
        tolerance=TOLERANCE,
        allow_short=ALLOW_SHORT,
        skip_cash_checks=SKIP_CASH_CHECKS,
        max_day_permutations=max(MAX_DAY_PERMUTATIONS, 1),
    )
    error_count, _ = print_report(rows=rows, issues=issues, show=SHOW_TOP_FINDINGS)

    if error_count > 0:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
