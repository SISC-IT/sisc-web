"""
Pairwise analysis for screener_mode once vs daily.

Example:
  python AI/tests/analyze_screener_mode_gap.py ^
      --start_day 2025-03-03 ^
      --end_day 2026-03-24 ^
      --models transformer ^
      --no-xai
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import subprocess
import sys
import time
import warnings
from dataclasses import asdict
from datetime import datetime
from pathlib import Path
from typing import Any

import pandas as pd


os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"

warnings.filterwarnings("ignore", category=UserWarning)
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", message=".*InconsistentVersionWarning.*")
warnings.filterwarnings("ignore", message=".*SQLAlchemy.*")

CURRENT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = CURRENT_DIR.parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.append(str(PROJECT_ROOT))

from AI.config import TradingConfig, load_trading_config
from AI.libs.database.connection import get_db_conn
import AI.backtests.run_backtest as run_backtest_module


def _trading_dates(start_day: str, end_day: str, db_name: str) -> list[str]:
    conn = get_db_conn(db_name)
    if conn is None:
        raise RuntimeError(f"DB connection failed for '{db_name}'.")

    try:
        query = """
            SELECT DISTINCT date
            FROM public.price_data
            WHERE date >= %s::date
              AND date <= %s::date
            ORDER BY date ASC
        """
        frame = pd.read_sql(query, conn, params=(start_day, end_day))
    finally:
        conn.close()

    if frame.empty:
        raise ValueError(f"No trading dates found in DB between {start_day} and {end_day}.")
    return pd.to_datetime(frame["date"], errors="coerce").dt.strftime("%Y-%m-%d").dropna().tolist()


def _run_shell(cmd: list[str]) -> str | None:
    try:
        out = subprocess.check_output(cmd, cwd=str(PROJECT_ROOT), stderr=subprocess.DEVNULL, text=True)
        return out.strip()
    except Exception:
        return None


def _to_builtin(value: Any) -> Any:
    if isinstance(value, (str, int, bool)) or value is None:
        return value
    if isinstance(value, float):
        if math.isnan(value) or math.isinf(value):
            return None
        return value
    if hasattr(value, "item"):
        return _to_builtin(value.item())
    if isinstance(value, dict):
        return {str(k): _to_builtin(v) for k, v in value.items()}
    if isinstance(value, (list, tuple, set)):
        return [_to_builtin(v) for v in value]
    return str(value)


def _assert_trading_day(day: str, label: str, db_name: str) -> None:
    conn = get_db_conn(db_name)
    if conn is None:
        raise RuntimeError(f"DB connection failed for '{db_name}'.")

    try:
        with conn.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) FROM public.price_data WHERE date = %s", (day,))
            count = int(cursor.fetchone()[0])
            if count <= 0:
                cursor.execute("SELECT MAX(date) FROM public.price_data")
                latest = cursor.fetchone()[0]
                raise ValueError(
                    f"{label}={day} is not a trading day in DB. "
                    f"Latest available trading date is {latest}."
                )
    finally:
        conn.close()


def _dedupe_keep_order(items: list[str]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for item in items:
        key = item.strip().upper()
        if not key:
            continue
        if key in seen:
            continue
        seen.add(key)
        ordered.append(key)
    return ordered


def _run_backtest_mode(
    *,
    mode: str,
    start_day: str,
    end_day: str,
    tickers: list[str],
    enable_xai: bool,
    sleep_seconds: float,
    trading_config: TradingConfig,
    models: list[str],
) -> tuple[run_backtest_module.DryRunPortfolioRepository, dict[str, list[str]], list[str], list[str]]:
    captured: dict[str, Any] = {}
    original = run_backtest_module._build_daily_ticker_plan

    def _capture_plan(*args: Any, **kwargs: Any) -> tuple[dict[str, list[str]], list[str]]:
        day_plan, universe = original(*args, **kwargs)
        normalized = {k: _dedupe_keep_order(v) for k, v in day_plan.items()}
        captured["day_plan"] = normalized
        captured["universe"] = list(_dedupe_keep_order(universe))
        return normalized, captured["universe"]

    run_backtest_module._build_daily_ticker_plan = _capture_plan
    try:
        repo = run_backtest_module._run_dryrun_backfill_backtest(
            start_day=start_day,
            end_day=end_day,
            tickers=tickers,
            enable_xai=enable_xai,
            sleep_seconds=sleep_seconds,
            trading_config=trading_config,
            active_models=models,
            screener_mode=mode,
        )
    finally:
        run_backtest_module._build_daily_ticker_plan = original

    if "day_plan" not in captured:
        raise RuntimeError(f"Failed to capture day_plan for mode={mode}.")

    dates = _trading_dates(start_day, end_day, trading_config.pipeline.db_name)
    day_plan = captured["day_plan"]
    for date_str in dates:
        day_plan.setdefault(date_str, [])
    return repo, day_plan, captured["universe"], dates


def _repo_to_frames(
    repo: run_backtest_module.DryRunPortfolioRepository,
    mode: str,
    initial_capital: float,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    summary_rows = []
    for date_str, row in repo.portfolio_summaries.items():
        summary_rows.append(
            {
                "mode": mode,
                "date": date_str,
                "run_id": f"daily_{date_str}",
                "total_asset": float(row["total_asset"]),
                "cash": float(row["cash"]),
                "market_value": float(row["market_value"]),
                "pnl_unrealized": float(row["pnl_unrealized"]),
                "pnl_realized_cum": float(row["pnl_realized_cum"]),
                "initial_capital": float(row["initial_capital"]),
                "return_rate": float(row["return_rate"]),
            }
        )

    summary_df = pd.DataFrame(summary_rows)
    if summary_df.empty:
        summary_df = pd.DataFrame(
            columns=[
                "mode",
                "date",
                "run_id",
                "total_asset",
                "cash",
                "market_value",
                "pnl_unrealized",
                "pnl_realized_cum",
                "initial_capital",
                "return_rate",
                "daily_return",
                "drawdown",
                "equity_curve",
            ]
        )
        equity_df = pd.DataFrame(columns=["mode", "date", "run_id", "equity", "daily_return", "drawdown"])
    else:
        summary_df = summary_df.sort_values("date").reset_index(drop=True)
        summary_df["daily_return"] = summary_df["total_asset"].pct_change().fillna(0.0)
        running_max = summary_df["total_asset"].cummax()
        summary_df["drawdown"] = summary_df["total_asset"] / running_max - 1.0
        summary_df["equity_curve"] = summary_df["total_asset"] / float(initial_capital)
        equity_df = summary_df[["mode", "date", "run_id", "equity_curve", "daily_return", "drawdown"]].rename(
            columns={"equity_curve": "equity"}
        )

    exec_df = pd.DataFrame(repo.executions)
    if exec_df.empty:
        exec_df = pd.DataFrame(
            columns=[
                "mode",
                "run_id",
                "ticker",
                "signal_date",
                "fill_date",
                "side",
                "signal",
                "qty",
                "fill_price",
                "value",
                "commission",
                "cash_after",
                "position_qty",
                "avg_price",
                "pnl_realized",
                "pnl_unrealized",
            ]
        )
    else:
        exec_df["mode"] = mode
        for col in [
            "qty",
            "fill_price",
            "value",
            "commission",
            "cash_after",
            "position_qty",
            "avg_price",
            "pnl_realized",
            "pnl_unrealized",
        ]:
            if col in exec_df.columns:
                exec_df[col] = pd.to_numeric(exec_df[col], errors="coerce")
        exec_df["fill_date"] = pd.to_datetime(exec_df["fill_date"]).dt.strftime("%Y-%m-%d")
        exec_df["signal_date"] = pd.to_datetime(exec_df["signal_date"]).dt.strftime("%Y-%m-%d")
        exec_df["ticker"] = exec_df["ticker"].astype(str).str.upper()
        exec_df["side"] = exec_df["side"].astype(str).str.upper()
        exec_df = exec_df.sort_values(["fill_date", "run_id", "ticker", "side"]).reset_index(drop=True)

    position_rows = []
    for date_str, tuples in repo.portfolio_positions.items():
        for row in tuples:
            (
                _date_value,
                ticker,
                position_qty,
                avg_price,
                current_price,
                market_value,
                pnl_unrealized,
                pnl_realized_cum,
            ) = row
            position_rows.append(
                {
                    "mode": mode,
                    "date": date_str,
                    "run_id": f"daily_{date_str}",
                    "ticker": str(ticker).upper(),
                    "position_qty": float(position_qty),
                    "avg_price": float(avg_price),
                    "current_price": float(current_price),
                    "market_value": float(market_value),
                    "pnl_unrealized": float(pnl_unrealized),
                    "pnl_realized_cum": float(pnl_realized_cum),
                }
            )

    positions_df = pd.DataFrame(position_rows)
    if positions_df.empty:
        positions_df = pd.DataFrame(
            columns=[
                "mode",
                "date",
                "run_id",
                "ticker",
                "position_qty",
                "avg_price",
                "current_price",
                "market_value",
                "pnl_unrealized",
                "pnl_realized_cum",
            ]
        )
    else:
        positions_df = positions_df.sort_values(["date", "ticker"]).reset_index(drop=True)

    return summary_df, equity_df, exec_df, positions_df


def _compute_avg_holding_days(executions_df: pd.DataFrame) -> float | None:
    if executions_df.empty:
        return None

    df = executions_df.copy()
    df["fill_date"] = pd.to_datetime(df["fill_date"], errors="coerce")
    df = df.dropna(subset=["fill_date", "ticker", "side", "qty"]).sort_values(["fill_date"]).reset_index(drop=True)

    lots_by_ticker: dict[str, list[list[Any]]] = {}
    total_weighted_days = 0.0
    total_closed_qty = 0.0

    for row in df.itertuples(index=False):
        ticker = str(row.ticker).upper()
        side = str(row.side).upper()
        qty = float(row.qty)
        fill_date = row.fill_date.to_pydatetime().date()
        if qty <= 0:
            continue

        if side == "BUY":
            lots_by_ticker.setdefault(ticker, []).append([qty, fill_date])
            continue

        if side != "SELL":
            continue

        lots = lots_by_ticker.setdefault(ticker, [])
        remaining = qty
        while remaining > 0 and lots:
            lot_qty, lot_date = lots[0]
            matched = min(lot_qty, remaining)
            hold_days = max(0, (fill_date - lot_date).days)
            total_weighted_days += hold_days * matched
            total_closed_qty += matched
            lot_qty -= matched
            remaining -= matched
            if lot_qty <= 1e-12:
                lots.pop(0)
            else:
                lots[0][0] = lot_qty

    if total_closed_qty <= 0:
        return None
    return total_weighted_days / total_closed_qty


def _compute_metrics(
    summary_df: pd.DataFrame,
    executions_df: pd.DataFrame,
    initial_capital: float,
) -> dict[str, Any]:
    if summary_df.empty:
        return {
            "business_days": 0,
            "final_total_asset": None,
            "final_return": None,
            "mdd": None,
            "volatility_ann": None,
            "sharpe_like": None,
            "trades_total": 0,
            "buy_count": 0,
            "sell_count": 0,
            "avg_holding_days": None,
            "turnover_ratio": None,
            "turnover_ann": None,
            "transaction_cost_total": 0.0,
        }

    daily_returns = pd.to_numeric(summary_df["daily_return"], errors="coerce").fillna(0.0)
    daily_std = float(daily_returns.std(ddof=0))
    daily_mean = float(daily_returns.mean())
    volatility_ann = daily_std * math.sqrt(252.0)
    sharpe_like = None if daily_std <= 0 else (daily_mean / daily_std) * math.sqrt(252.0)
    final_asset = float(summary_df["total_asset"].iloc[-1])
    final_return = final_asset / float(initial_capital) - 1.0
    mdd = float(pd.to_numeric(summary_df["drawdown"], errors="coerce").min())

    trades_total = int(len(executions_df))
    buy_count = int((executions_df["side"] == "BUY").sum()) if not executions_df.empty else 0
    sell_count = int((executions_df["side"] == "SELL").sum()) if not executions_df.empty else 0
    transaction_cost_total = float(pd.to_numeric(executions_df.get("commission", 0.0), errors="coerce").fillna(0.0).sum())

    traded_value = float(pd.to_numeric(executions_df.get("value", 0.0), errors="coerce").fillna(0.0).sum())
    avg_asset = float(pd.to_numeric(summary_df["total_asset"], errors="coerce").mean())
    turnover_ratio = None if avg_asset <= 0 else traded_value / avg_asset
    turnover_ann = None
    if turnover_ratio is not None and len(summary_df) > 0:
        turnover_ann = turnover_ratio / float(len(summary_df)) * 252.0

    return {
        "business_days": int(len(summary_df)),
        "final_total_asset": final_asset,
        "final_return": final_return,
        "mdd": mdd,
        "volatility_ann": volatility_ann,
        "sharpe_like": sharpe_like,
        "trades_total": trades_total,
        "buy_count": buy_count,
        "sell_count": sell_count,
        "avg_holding_days": _compute_avg_holding_days(executions_df),
        "turnover_ratio": turnover_ratio,
        "turnover_ann": turnover_ann,
        "transaction_cost_total": transaction_cost_total,
    }


def _build_universe_frames(
    dates: list[str],
    once_plan: dict[str, list[str]],
    daily_plan: dict[str, list[str]],
) -> tuple[pd.DataFrame, pd.DataFrame]:
    plan_rows = []
    diff_rows = []

    prev_once: set[str] | None = None
    prev_daily: set[str] | None = None

    for date_str in dates:
        once_set = set(once_plan.get(date_str, []))
        daily_set = set(daily_plan.get(date_str, []))

        plan_rows.append(
            {
                "date": date_str,
                "mode": "once",
                "size": len(once_set),
                "tickers": ",".join(sorted(once_set)),
            }
        )
        plan_rows.append(
            {
                "date": date_str,
                "mode": "daily",
                "size": len(daily_set),
                "tickers": ",".join(sorted(daily_set)),
            }
        )

        union = once_set | daily_set
        inter = once_set & daily_set
        inter_overlap = 1.0 if not union else len(inter) / len(union)

        daily_added = []
        daily_removed = []
        daily_overlap_prev = None
        daily_churn = None
        if prev_daily is not None:
            daily_added = sorted(daily_set - prev_daily)
            daily_removed = sorted(prev_daily - daily_set)
            prev_union = prev_daily | daily_set
            prev_inter = prev_daily & daily_set
            daily_overlap_prev = 1.0 if not prev_union else len(prev_inter) / len(prev_union)
            daily_churn = 1.0 - daily_overlap_prev

        once_overlap_prev = None
        once_churn = None
        if prev_once is not None:
            prev_union = prev_once | once_set
            prev_inter = prev_once & once_set
            once_overlap_prev = 1.0 if not prev_union else len(prev_inter) / len(prev_union)
            once_churn = 1.0 - once_overlap_prev

        diff_rows.append(
            {
                "date": date_str,
                "once_size": len(once_set),
                "daily_size": len(daily_set),
                "inter_mode_overlap_ratio": inter_overlap,
                "daily_vs_once_added_count": len(daily_set - once_set),
                "daily_vs_once_removed_count": len(once_set - daily_set),
                "daily_prev_overlap_ratio": daily_overlap_prev,
                "daily_churn_rate": daily_churn,
                "daily_added_count": len(daily_added),
                "daily_removed_count": len(daily_removed),
                "daily_added_tickers": ",".join(daily_added),
                "daily_removed_tickers": ",".join(daily_removed),
                "once_prev_overlap_ratio": once_overlap_prev,
                "once_churn_rate": once_churn,
            }
        )

        prev_once = once_set
        prev_daily = daily_set

    plan_df = pd.DataFrame(plan_rows).sort_values(["date", "mode"]).reset_index(drop=True)
    diff_df = pd.DataFrame(diff_rows).sort_values("date").reset_index(drop=True)

    return plan_df, diff_df


def _build_drop_mapping(
    dates: list[str],
    daily_plan: dict[str, list[str]],
    daily_positions: pd.DataFrame,
    daily_executions: pd.DataFrame,
) -> tuple[pd.DataFrame, dict[str, Any]]:
    pos_map: dict[tuple[str, str], float] = {}
    for row in daily_positions.itertuples(index=False):
        pos_map[(str(row.date), str(row.ticker).upper())] = float(row.position_qty)

    sell_df = daily_executions[daily_executions["side"] == "SELL"].copy()
    if sell_df.empty:
        sell_stats = pd.DataFrame(columns=["fill_date", "ticker", "sell_qty", "sell_value", "sell_pnl_realized"])
    else:
        sell_stats = (
            sell_df.groupby(["fill_date", "ticker"], as_index=False)
            .agg(
                sell_qty=("qty", "sum"),
                sell_value=("value", "sum"),
                sell_pnl_realized=("pnl_realized", "sum"),
            )
            .reset_index(drop=True)
        )

    sell_map_qty = {(str(r.fill_date), str(r.ticker).upper()): float(r.sell_qty) for r in sell_stats.itertuples(index=False)}
    sell_map_value = {(str(r.fill_date), str(r.ticker).upper()): float(r.sell_value) for r in sell_stats.itertuples(index=False)}
    sell_map_pnl = {(str(r.fill_date), str(r.ticker).upper()): float(r.sell_pnl_realized) for r in sell_stats.itertuples(index=False)}

    date_to_idx = {d: idx for idx, d in enumerate(dates)}

    def _exit_lag(drop_date: str, ticker: str) -> int | None:
        start_idx = date_to_idx.get(drop_date)
        if start_idx is None:
            return None
        for idx in range(start_idx, len(dates)):
            cur_date = dates[idx]
            qty = pos_map.get((cur_date, ticker), 0.0)
            if qty <= 0:
                return idx - start_idx
        return None

    rows = []
    for idx in range(1, len(dates)):
        prev_date = dates[idx - 1]
        cur_date = dates[idx]
        prev_set = set(daily_plan.get(prev_date, []))
        cur_set = set(daily_plan.get(cur_date, []))
        removed = sorted(prev_set - cur_set)
        for ticker in removed:
            qty_prev = pos_map.get((prev_date, ticker), 0.0)
            qty_cur = pos_map.get((cur_date, ticker), 0.0)
            held_prev = qty_prev > 0
            sold_qty_today = sell_map_qty.get((cur_date, ticker), 0.0)
            sold_today = sold_qty_today > 0
            exited_eod = held_prev and qty_cur <= 0
            kept_eod = held_prev and qty_cur > 0
            rows.append(
                {
                    "date": cur_date,
                    "ticker": ticker,
                    "prev_date": prev_date,
                    "was_held_prev_day": held_prev,
                    "qty_prev_day": qty_prev,
                    "qty_eod_drop_day": qty_cur,
                    "sold_on_drop_day": sold_today,
                    "sold_qty_drop_day": sold_qty_today,
                    "sold_value_drop_day": sell_map_value.get((cur_date, ticker), 0.0),
                    "sold_realized_pnl_drop_day": sell_map_pnl.get((cur_date, ticker), 0.0),
                    "exited_by_eod_drop_day": exited_eod,
                    "kept_after_drop_day": kept_eod,
                    "exit_lag_business_days": _exit_lag(cur_date, ticker) if held_prev else None,
                }
            )

    details_df = pd.DataFrame(rows)
    if details_df.empty:
        metrics = {
            "drop_events_total": 0,
            "drop_events_held_prev": 0,
            "held_prev_sold_on_drop_day": 0,
            "held_prev_exited_eod_drop_day": 0,
            "held_prev_kept_after_drop_day": 0,
            "held_prev_avg_exit_lag_bdays": None,
            "held_prev_drop_day_realized_pnl_sum": 0.0,
        }
        return details_df, metrics

    held_df = details_df[details_df["was_held_prev_day"] == True]
    metrics = {
        "drop_events_total": int(len(details_df)),
        "drop_events_held_prev": int(len(held_df)),
        "held_prev_sold_on_drop_day": int((held_df["sold_on_drop_day"] == True).sum()) if not held_df.empty else 0,
        "held_prev_exited_eod_drop_day": int((held_df["exited_by_eod_drop_day"] == True).sum()) if not held_df.empty else 0,
        "held_prev_kept_after_drop_day": int((held_df["kept_after_drop_day"] == True).sum()) if not held_df.empty else 0,
        "held_prev_avg_exit_lag_bdays": (
            float(pd.to_numeric(held_df["exit_lag_business_days"], errors="coerce").dropna().mean())
            if not held_df.empty
            else None
        ),
        "held_prev_drop_day_realized_pnl_sum": (
            float(pd.to_numeric(held_df["sold_realized_pnl_drop_day"], errors="coerce").fillna(0.0).sum())
            if not held_df.empty
            else 0.0
        ),
    }
    return details_df, metrics


def _build_alignment_df(mode_to_data: dict[str, dict[str, pd.DataFrame]]) -> pd.DataFrame:
    checks: list[dict[str, Any]] = []
    for mode, data in mode_to_data.items():
        for name, df in data.items():
            if name == "executions":
                keys = ["fill_date", "ticker", "run_id"]
            elif name == "positions":
                keys = ["date", "ticker", "run_id"]
            else:
                keys = ["date", "run_id"]

            missing_cols = [col for col in keys if col not in df.columns]
            if missing_cols:
                checks.append(
                    {
                        "mode": mode,
                        "dataset": name,
                        "keys": ",".join(keys),
                        "missing_key_columns": ",".join(missing_cols),
                        "null_key_rows": None,
                        "duplicate_key_rows": None,
                        "total_rows": int(len(df)),
                    }
                )
                continue

            null_rows = int(df[keys].isnull().any(axis=1).sum())
            dup_rows = int(df.duplicated(subset=keys, keep=False).sum())
            checks.append(
                {
                    "mode": mode,
                    "dataset": name,
                    "keys": ",".join(keys),
                    "missing_key_columns": "",
                    "null_key_rows": null_rows,
                    "duplicate_key_rows": dup_rows,
                    "total_rows": int(len(df)),
                }
            )
    return pd.DataFrame(checks)


def _build_metrics_comparison(once_metrics: dict[str, Any], daily_metrics: dict[str, Any]) -> pd.DataFrame:
    metric_names = sorted(set(once_metrics.keys()) | set(daily_metrics.keys()))
    rows = []
    for name in metric_names:
        once_val = once_metrics.get(name)
        daily_val = daily_metrics.get(name)
        delta = None
        if isinstance(once_val, (int, float)) and isinstance(daily_val, (int, float)):
            if not (math.isnan(float(once_val)) or math.isnan(float(daily_val))):
                delta = float(daily_val) - float(once_val)
        rows.append({"metric": name, "once": once_val, "daily": daily_val, "delta_daily_minus_once": delta})
    return pd.DataFrame(rows)


def _build_attribution_df(summary_once: pd.DataFrame, summary_daily: pd.DataFrame, diff_df: pd.DataFrame) -> tuple[pd.DataFrame, dict[str, Any]]:
    lhs = summary_once[["date", "daily_return"]].rename(columns={"daily_return": "once_daily_return"})
    rhs = summary_daily[["date", "daily_return"]].rename(columns={"daily_return": "daily_daily_return"})
    merged = lhs.merge(rhs, on="date", how="inner")
    merged = merged.merge(diff_df[["date", "inter_mode_overlap_ratio", "daily_churn_rate"]], on="date", how="left")
    merged["return_diff"] = merged["daily_daily_return"] - merged["once_daily_return"]
    merged["return_diff_bps"] = merged["return_diff"] * 10000.0
    merged["is_universe_diff_day"] = merged["inter_mode_overlap_ratio"] < 1.0
    merged["is_daily_churn_day"] = pd.to_numeric(merged["daily_churn_rate"], errors="coerce").fillna(0.0) > 0.0

    summary = {
        "return_diff_bps_total": float(merged["return_diff_bps"].sum()) if not merged.empty else 0.0,
        "return_diff_bps_universe_diff_days": float(merged.loc[merged["is_universe_diff_day"], "return_diff_bps"].sum()) if not merged.empty else 0.0,
        "return_diff_bps_daily_churn_days": float(merged.loc[merged["is_daily_churn_day"], "return_diff_bps"].sum()) if not merged.empty else 0.0,
        "return_diff_bps_no_daily_churn_days": float(merged.loc[~merged["is_daily_churn_day"], "return_diff_bps"].sum()) if not merged.empty else 0.0,
        "mean_inter_mode_overlap_ratio": float(pd.to_numeric(merged["inter_mode_overlap_ratio"], errors="coerce").mean()) if not merged.empty else None,
        "mean_daily_churn_rate": float(pd.to_numeric(merged["daily_churn_rate"], errors="coerce").mean()) if not merged.empty else None,
    }
    return merged, summary


def _write_report_md(
    output_path: Path,
    metadata: dict[str, Any],
    once_metrics: dict[str, Any],
    daily_metrics: dict[str, Any],
    drop_metrics: dict[str, Any],
    attribution_summary: dict[str, Any],
) -> None:
    def pct(v: Any) -> str:
        if v is None:
            return "NA"
        return f"{float(v) * 100.0:.2f}%"

    def num(v: Any) -> str:
        if v is None:
            return "NA"
        return f"{float(v):,.4f}"

    lines = []
    lines.append("# Screener Mode Analysis (once vs daily)")
    lines.append("")
    lines.append("## Run Conditions")
    lines.append(f"- start_day: {metadata['start_day']}")
    lines.append(f"- end_day: {metadata['end_day']}")
    lines.append(f"- models: {', '.join(metadata['models'])}")
    lines.append(f"- xai: {'ON' if metadata['enable_xai'] else 'OFF'}")
    lines.append(f"- db_name: {metadata['db_name']}")
    lines.append(f"- git_commit: {metadata.get('git_commit')}")
    lines.append(f"- git_dirty: {metadata.get('git_dirty')}")
    lines.append("")
    lines.append("## Performance")
    lines.append("| metric | once | daily | delta(daily-once) |")
    lines.append("|---|---:|---:|---:|")
    lines.append(f"| Final Return | {pct(once_metrics.get('final_return'))} | {pct(daily_metrics.get('final_return'))} | {pct((daily_metrics.get('final_return') or 0) - (once_metrics.get('final_return') or 0))} |")
    lines.append(f"| MDD | {pct(once_metrics.get('mdd'))} | {pct(daily_metrics.get('mdd'))} | {pct((daily_metrics.get('mdd') or 0) - (once_metrics.get('mdd') or 0))} |")
    lines.append(f"| Volatility (ann) | {pct(once_metrics.get('volatility_ann'))} | {pct(daily_metrics.get('volatility_ann'))} | {pct((daily_metrics.get('volatility_ann') or 0) - (once_metrics.get('volatility_ann') or 0))} |")
    lines.append(f"| Sharpe-like | {num(once_metrics.get('sharpe_like'))} | {num(daily_metrics.get('sharpe_like'))} | {num((daily_metrics.get('sharpe_like') or 0) - (once_metrics.get('sharpe_like') or 0))} |")
    lines.append(f"| Trades (total) | {num(once_metrics.get('trades_total'))} | {num(daily_metrics.get('trades_total'))} | {num((daily_metrics.get('trades_total') or 0) - (once_metrics.get('trades_total') or 0))} |")
    lines.append(f"| Avg Holding Days | {num(once_metrics.get('avg_holding_days'))} | {num(daily_metrics.get('avg_holding_days'))} | {num((daily_metrics.get('avg_holding_days') or 0) - (once_metrics.get('avg_holding_days') or 0))} |")
    lines.append(f"| Turnover Ratio | {num(once_metrics.get('turnover_ratio'))} | {num(daily_metrics.get('turnover_ratio'))} | {num((daily_metrics.get('turnover_ratio') or 0) - (once_metrics.get('turnover_ratio') or 0))} |")
    lines.append(f"| Transaction Cost | {num(once_metrics.get('transaction_cost_total'))} | {num(daily_metrics.get('transaction_cost_total'))} | {num((daily_metrics.get('transaction_cost_total') or 0) - (once_metrics.get('transaction_cost_total') or 0))} |")
    lines.append("")
    lines.append("## Universe -> Trade Mapping (daily)")
    lines.append(f"- drop_events_total: {drop_metrics.get('drop_events_total')}")
    lines.append(f"- drop_events_held_prev: {drop_metrics.get('drop_events_held_prev')}")
    lines.append(f"- held_prev_sold_on_drop_day: {drop_metrics.get('held_prev_sold_on_drop_day')}")
    lines.append(f"- held_prev_exited_eod_drop_day: {drop_metrics.get('held_prev_exited_eod_drop_day')}")
    lines.append(f"- held_prev_kept_after_drop_day: {drop_metrics.get('held_prev_kept_after_drop_day')}")
    lines.append(f"- held_prev_avg_exit_lag_bdays: {num(drop_metrics.get('held_prev_avg_exit_lag_bdays'))}")
    lines.append("")
    lines.append("## Attribution Hints")
    lines.append(f"- return_diff_bps_total: {num(attribution_summary.get('return_diff_bps_total'))}")
    lines.append(f"- return_diff_bps_universe_diff_days: {num(attribution_summary.get('return_diff_bps_universe_diff_days'))}")
    lines.append(f"- return_diff_bps_daily_churn_days: {num(attribution_summary.get('return_diff_bps_daily_churn_days'))}")
    lines.append(f"- mean_inter_mode_overlap_ratio: {num(attribution_summary.get('mean_inter_mode_overlap_ratio'))}")
    lines.append(f"- mean_daily_churn_rate: {num(attribution_summary.get('mean_daily_churn_rate'))}")
    lines.append("")
    output_path.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    bootstrap_parser = argparse.ArgumentParser(add_help=False)
    bootstrap_parser.add_argument("--config", type=str, default=None, help="Optional trading config override JSON.")
    bootstrap_args, _ = bootstrap_parser.parse_known_args()
    trading_config = load_trading_config(bootstrap_args.config)

    parser = argparse.ArgumentParser(
        parents=[bootstrap_parser],
        description="Analyze performance gap between screener_mode=once and screener_mode=daily.",
    )
    parser.add_argument("--start_day", type=str, default="2025-03-03", help="Start day (YYYY-MM-DD)")
    parser.add_argument("--end_day", type=str, default="2026-03-24", help="End day (YYYY-MM-DD)")
    parser.add_argument("--models", type=str, default=",".join(trading_config.pipeline.active_models), help="Comma-separated model list.")
    parser.add_argument("--tickers", type=str, default="", help="Comma-separated static tickers. Empty uses screener.")
    parser.add_argument("--sleep", type=float, default=0.0, help="Sleep seconds between days.")
    xai_group = parser.add_mutually_exclusive_group()
    xai_group.add_argument("--xai", dest="enable_xai", action="store_true", help="Enable XAI generation")
    xai_group.add_argument("--no-xai", dest="enable_xai", action="store_false", help="Disable XAI generation")
    parser.set_defaults(enable_xai=False)
    parser.add_argument(
        "--output_dir",
        type=str,
        default=str(PROJECT_ROOT / "AI" / "backtests" / "results"),
        help="Output root directory",
    )
    args = parser.parse_args()

    try:
        start_ts = pd.to_datetime(args.start_day)
        end_ts = pd.to_datetime(args.end_day)
    except Exception:
        parser.error("Invalid date format. Use YYYY-MM-DD for --start_day/--end_day.")
    if start_ts > end_ts:
        parser.error("--start_day must be <= --end_day.")

    _assert_trading_day(args.start_day, "start_day", trading_config.pipeline.db_name)
    _assert_trading_day(args.end_day, "end_day", trading_config.pipeline.db_name)

    model_list = [m.strip().lower() for m in args.models.split(",") if m.strip()]
    if not model_list:
        parser.error("At least one model must be supplied via --models.")
    ticker_list = _dedupe_keep_order([t.strip() for t in args.tickers.split(",") if t.strip()])

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_root = Path(args.output_dir).resolve() / f"screener_mode_analysis_{timestamp}"
    output_root.mkdir(parents=True, exist_ok=True)

    print("== Screener Mode Pair Analysis Start ==")
    print(f"- Date range: {args.start_day} ~ {args.end_day}")
    print(f"- Models: {model_list}")
    print(f"- Tickers: {ticker_list if ticker_list else 'dynamic screener'}")
    print(f"- Output: {output_root}")

    mode_to_artifacts: dict[str, dict[str, Any]] = {}
    mode_to_dataframes: dict[str, dict[str, pd.DataFrame]] = {}
    for mode in ["once", "daily"]:
        max_attempts = 3
        attempt = 0
        while True:
            attempt += 1
            print(f"\n[Run] mode={mode} | attempt={attempt}/{max_attempts}")
            try:
                repo, day_plan, universe, dates = _run_backtest_mode(
                    mode=mode,
                    start_day=args.start_day,
                    end_day=args.end_day,
                    tickers=ticker_list,
                    enable_xai=args.enable_xai,
                    sleep_seconds=args.sleep,
                    trading_config=trading_config,
                    models=model_list,
                )
                summary_df, equity_df, executions_df, positions_df = _repo_to_frames(
                    repo=repo,
                    mode=mode,
                    initial_capital=float(trading_config.pipeline.initial_capital),
                )
                metrics = _compute_metrics(
                    summary_df=summary_df,
                    executions_df=executions_df,
                    initial_capital=float(trading_config.pipeline.initial_capital),
                )
                mode_to_artifacts[mode] = {
                    "repo": repo,
                    "day_plan": day_plan,
                    "universe": universe,
                    "dates": dates,
                    "metrics": metrics,
                }
                mode_to_dataframes[mode] = {
                    "summary": summary_df,
                    "equity": equity_df,
                    "executions": executions_df,
                    "positions": positions_df,
                }
                break
            except Exception as error:
                if attempt >= max_attempts:
                    raise
                print(f"[Warning] mode={mode} attempt={attempt} failed: {error}")
                print("[Warning] retrying in 5 seconds...")
                time.sleep(5)

    analysis_dates = mode_to_artifacts["once"]["dates"]
    once_plan = mode_to_artifacts["once"]["day_plan"]
    daily_plan = mode_to_artifacts["daily"]["day_plan"]

    universe_plan_df, universe_diff_df = _build_universe_frames(analysis_dates, once_plan, daily_plan)
    drop_details_df, drop_metrics = _build_drop_mapping(
        dates=analysis_dates,
        daily_plan=daily_plan,
        daily_positions=mode_to_dataframes["daily"]["positions"],
        daily_executions=mode_to_dataframes["daily"]["executions"],
    )
    alignment_df = _build_alignment_df(mode_to_dataframes)
    metrics_df = _build_metrics_comparison(
        mode_to_artifacts["once"]["metrics"],
        mode_to_artifacts["daily"]["metrics"],
    )
    attribution_df, attribution_summary = _build_attribution_df(
        summary_once=mode_to_dataframes["once"]["summary"],
        summary_daily=mode_to_dataframes["daily"]["summary"],
        diff_df=universe_diff_df,
    )

    for mode in ["once", "daily"]:
        mode_dir = output_root / mode
        mode_dir.mkdir(parents=True, exist_ok=True)
        mode_to_dataframes[mode]["summary"].to_csv(mode_dir / "summary.csv", index=False, encoding="utf-8-sig")
        mode_to_dataframes[mode]["equity"].to_csv(mode_dir / "equity.csv", index=False, encoding="utf-8-sig")
        mode_to_dataframes[mode]["executions"].to_csv(mode_dir / "executions.csv", index=False, encoding="utf-8-sig")
        mode_to_dataframes[mode]["positions"].to_csv(mode_dir / "positions.csv", index=False, encoding="utf-8-sig")
        pd.DataFrame(
            {
                "date": analysis_dates,
                "tickers": [",".join(mode_to_artifacts[mode]["day_plan"].get(date_str, [])) for date_str in analysis_dates],
                "universe_size": [len(mode_to_artifacts[mode]["day_plan"].get(date_str, [])) for date_str in analysis_dates],
            }
        ).to_csv(mode_dir / "universe_daily_plan.csv", index=False, encoding="utf-8-sig")
        with (mode_dir / "metrics.json").open("w", encoding="utf-8") as handle:
            json.dump(_to_builtin(mode_to_artifacts[mode]["metrics"]), handle, indent=2, ensure_ascii=False)

    universe_plan_df.to_csv(output_root / "universe_plan_all.csv", index=False, encoding="utf-8-sig")
    universe_diff_df.to_csv(output_root / "screener_diff.csv", index=False, encoding="utf-8-sig")
    drop_details_df.to_csv(output_root / "drop_mapping_details.csv", index=False, encoding="utf-8-sig")
    alignment_df.to_csv(output_root / "key_alignment_check.csv", index=False, encoding="utf-8-sig")
    metrics_df.to_csv(output_root / "metrics_comparison.csv", index=False, encoding="utf-8-sig")
    attribution_df.to_csv(output_root / "attribution_daily_return_diff.csv", index=False, encoding="utf-8-sig")

    config_payload = asdict(trading_config)
    config_hash = hashlib.sha256(
        json.dumps(config_payload, sort_keys=True, ensure_ascii=False, default=str).encode("utf-8")
    ).hexdigest()

    git_commit = _run_shell(["git", "rev-parse", "HEAD"])
    git_status = _run_shell(["git", "status", "--porcelain"]) or ""
    metadata = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "start_day": args.start_day,
        "end_day": args.end_day,
        "models": model_list,
        "tickers": ticker_list,
        "enable_xai": bool(args.enable_xai),
        "db_name": trading_config.pipeline.db_name,
        "initial_capital": float(trading_config.pipeline.initial_capital),
        "config_path": bootstrap_args.config,
        "config_hash_sha256": config_hash,
        "git_commit": git_commit,
        "git_dirty": bool(git_status.strip()),
        "git_status_porcelain": git_status.splitlines(),
        "drop_metrics": drop_metrics,
        "attribution_summary": attribution_summary,
    }
    with (output_root / "run_metadata.json").open("w", encoding="utf-8") as handle:
        json.dump(_to_builtin(metadata), handle, indent=2, ensure_ascii=False)

    _write_report_md(
        output_path=output_root / "report.md",
        metadata=metadata,
        once_metrics=mode_to_artifacts["once"]["metrics"],
        daily_metrics=mode_to_artifacts["daily"]["metrics"],
        drop_metrics=drop_metrics,
        attribution_summary=attribution_summary,
    )

    print("\n== Screener Mode Pair Analysis Finished ==")
    print(f"- Output directory: {output_root}")
    once_final_return = mode_to_artifacts["once"]["metrics"]["final_return"]
    daily_final_return = mode_to_artifacts["daily"]["metrics"]["final_return"]
    print(f"- once final return: {once_final_return}")
    print(f"- daily final return: {daily_final_return}")
    if once_final_return is not None and daily_final_return is not None:
        print(f"- final return gap (daily-once): {daily_final_return - once_final_return}")
    else:
        print("- final return gap (daily-once): NA")


if __name__ == "__main__":
    main()
