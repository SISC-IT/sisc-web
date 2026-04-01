"""
Portfolio evaluation utility for daily-routine trading outputs.

Example:
  python AI/tests/portfolio_evaluation.py \
      --summary_csv AI/tests/out/screener_mode_analysis_xxx/daily/summary.csv \
      --executions_csv AI/tests/out/screener_mode_analysis_xxx/daily/executions.csv \
      --initial_capital 10000 \
      --output_dir AI/backtests/results/portfolio_eval
"""

from __future__ import annotations

import argparse
import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import pandas as pd


TRADING_DAYS_PER_YEAR = 252.0


@dataclass(frozen=True, slots=True)
class EvaluationResult:
    metrics: dict[str, Any]
    daily: pd.DataFrame
    monthly_returns: pd.DataFrame


def _to_numeric(series: pd.Series, default: float | None = 0.0) -> pd.Series:
    numeric = pd.to_numeric(series, errors="coerce")
    if default is None:
        return numeric
    return numeric.fillna(default)


def _to_numeric_strict(series: pd.Series, field_name: str) -> pd.Series:
    numeric = pd.to_numeric(series, errors="coerce")
    invalid_count = int(numeric.isna().sum())
    if invalid_count > 0:
        raise ValueError(f"{field_name} contains {invalid_count} non-numeric or missing values.")
    return numeric.astype("float64")


def _ensure_side_upper(executions_df: pd.DataFrame) -> pd.Series:
    if executions_df.empty:
        return pd.Series([], dtype="string")
    if "side" not in executions_df.columns:
        return pd.Series([""] * len(executions_df), index=executions_df.index, dtype="string")
    return executions_df["side"].astype(str).str.upper()


def prepare_summary_frame(summary_df: pd.DataFrame, initial_capital: float) -> pd.DataFrame:
    """
    Normalize summary dataframe and derive daily_return/drawdown/equity_curve.
    """
    if summary_df is None or summary_df.empty:
        return pd.DataFrame(
            columns=[
                "date",
                "total_asset",
                "daily_return",
                "drawdown",
                "equity_curve",
            ]
        )
    if "total_asset" not in summary_df.columns:
        raise ValueError("summary_df must include 'total_asset' column.")

    frame = summary_df.copy()

    if "date" in frame.columns:
        frame["date"] = pd.to_datetime(frame["date"], errors="coerce")
        frame = frame.sort_values("date").reset_index(drop=True)
    else:
        frame = frame.reset_index(drop=True)

    frame["total_asset"] = _to_numeric_strict(frame["total_asset"], "summary_df.total_asset")
    frame["daily_return"] = (
        _to_numeric_strict(frame["daily_return"], "summary_df.daily_return")
        if "daily_return" in frame.columns
        else frame["total_asset"].pct_change().fillna(0.0)
    )

    running_max = frame["total_asset"].cummax()
    frame["drawdown"] = (
        _to_numeric_strict(frame["drawdown"], "summary_df.drawdown")
        if "drawdown" in frame.columns
        else frame["total_asset"] / running_max - 1.0
    )

    if initial_capital <= 0:
        raise ValueError("initial_capital must be greater than 0.")
    frame["equity_curve"] = frame["total_asset"] / float(initial_capital)
    return frame


def compute_avg_holding_days(executions_df: pd.DataFrame) -> float | None:
    """
    FIFO-based weighted average holding period (business-calendar agnostic, date diff in days).
    """
    if executions_df is None or executions_df.empty:
        return None

    required_cols = {"ticker", "side", "qty", "fill_date"}
    if not required_cols.issubset(set(executions_df.columns)):
        return None

    frame = executions_df.copy()
    frame["fill_date"] = pd.to_datetime(frame["fill_date"], errors="coerce")
    frame["qty"] = _to_numeric(frame["qty"])
    frame["side"] = frame["side"].astype(str).str.upper()
    frame = frame.dropna(subset=["fill_date"])
    frame = frame.sort_values("fill_date")

    # lots[ticker] = [[remaining_qty, buy_date], ...]
    lots: dict[str, list[list[Any]]] = {}
    total_weighted_days = 0.0
    total_closed_qty = 0.0

    for _, row in frame.iterrows():
        ticker = str(row["ticker"])
        side = str(row["side"])
        qty = float(row["qty"])
        fill_date = row["fill_date"]

        if qty <= 0:
            continue

        ticker_lots = lots.setdefault(ticker, [])
        if side == "BUY":
            ticker_lots.append([qty, fill_date])
            continue
        if side != "SELL":
            continue

        remaining = qty
        while remaining > 1e-12 and ticker_lots:
            lot_qty, lot_date = ticker_lots[0]
            matched_qty = min(lot_qty, remaining)
            hold_days = max(0.0, float((fill_date - lot_date).days))
            total_weighted_days += hold_days * matched_qty
            total_closed_qty += matched_qty

            lot_qty -= matched_qty
            remaining -= matched_qty
            if lot_qty <= 1e-12:
                ticker_lots.pop(0)
            else:
                ticker_lots[0][0] = lot_qty

    if total_closed_qty <= 0:
        return None
    return total_weighted_days / total_closed_qty


def compute_risk_metrics(
    daily_returns: pd.Series,
    risk_free_rate: float = 0.0,
    var_confidence: float = 0.95,
) -> dict[str, float | None]:
    returns = _to_numeric(daily_returns)
    if returns.empty:
        return {
            "volatility_ann": None,
            "downside_vol_ann": None,
            "sharpe": None,
            "sortino": None,
            "var": None,
            "cvar": None,
        }

    rf_daily = float(risk_free_rate) / TRADING_DAYS_PER_YEAR
    excess = returns - rf_daily
    std = float(excess.std(ddof=0))
    downside = excess[excess < 0.0]
    downside_std = float(downside.std(ddof=0)) if not downside.empty else 0.0

    var_alpha = 1.0 - float(var_confidence)
    var_value = float(returns.quantile(var_alpha))
    tail = returns[returns <= var_value]
    cvar_value = float(tail.mean()) if not tail.empty else None

    return {
        "volatility_ann": std * math.sqrt(TRADING_DAYS_PER_YEAR),
        "downside_vol_ann": downside_std * math.sqrt(TRADING_DAYS_PER_YEAR),
        "sharpe": None if std <= 0 else (float(excess.mean()) / std) * math.sqrt(TRADING_DAYS_PER_YEAR),
        "sortino": None
        if downside_std <= 0
        else (float(excess.mean()) / downside_std) * math.sqrt(TRADING_DAYS_PER_YEAR),
        "var": var_value,
        "cvar": cvar_value,
    }


def _resolve_traded_value(executions_df: pd.DataFrame) -> pd.Series:
    if executions_df.empty:
        return pd.Series([], dtype="float64")
    if "value" in executions_df.columns:
        return _to_numeric(executions_df["value"]).abs()
    if {"qty", "fill_price"}.issubset(set(executions_df.columns)):
        return (_to_numeric(executions_df["qty"]) * _to_numeric(executions_df["fill_price"])).abs()
    return pd.Series([0.0] * len(executions_df))


def evaluate_portfolio(
    summary_df: pd.DataFrame,
    executions_df: pd.DataFrame | None,
    *,
    initial_capital: float,
    risk_free_rate: float = 0.0,
    var_confidence: float = 0.95,
) -> EvaluationResult:
    daily = prepare_summary_frame(summary_df, initial_capital=initial_capital)
    executions = executions_df.copy() if executions_df is not None else pd.DataFrame()

    if daily.empty:
        metrics = {
            "business_days": 0,
            "final_total_asset": None,
            "final_return": None,
            "cagr": None,
            "max_drawdown": None,
            "volatility_ann": None,
            "downside_vol_ann": None,
            "sharpe": None,
            "sortino": None,
            "calmar": None,
            "var_confidence": var_confidence,
            "var": None,
            "cvar": None,
            "trades_total": 0,
            "buy_count": 0,
            "sell_count": 0,
            "turnover_ratio": None,
            "turnover_ann": None,
            "transaction_cost_total": 0.0,
            "avg_holding_days": None,
            "realized_win_rate": None,
            "profit_factor": None,
            "initial_capital": float(initial_capital),
        }
        return EvaluationResult(metrics=metrics, daily=daily, monthly_returns=pd.DataFrame(columns=["month", "return"]))

    daily_returns = _to_numeric(daily["daily_return"])
    risk_metrics = compute_risk_metrics(
        daily_returns=daily_returns,
        risk_free_rate=risk_free_rate,
        var_confidence=var_confidence,
    )

    final_asset = float(daily["total_asset"].iloc[-1])
    final_return = final_asset / float(initial_capital) - 1.0
    mdd = float(_to_numeric(daily["drawdown"]).min())
    business_days = int(len(daily))
    years = business_days / TRADING_DAYS_PER_YEAR
    cagr = None
    if years > 0 and initial_capital > 0 and final_asset > 0:
        cagr = (final_asset / float(initial_capital)) ** (1.0 / years) - 1.0
    calmar = None
    if cagr is not None and mdd < 0:
        calmar = cagr / abs(mdd)

    side = _ensure_side_upper(executions)
    traded_value = float(_resolve_traded_value(executions).sum())
    avg_asset = float(_to_numeric(daily["total_asset"]).mean())
    turnover_ratio = None if avg_asset <= 0 else traded_value / avg_asset
    turnover_ann = None
    if turnover_ratio is not None and business_days > 0:
        turnover_ann = turnover_ratio / float(business_days) * TRADING_DAYS_PER_YEAR

    transaction_cost_total = (
        float(_to_numeric(executions["commission"]).sum()) if "commission" in executions.columns else 0.0
    )

    pnl_realized_series = (
        _to_numeric(executions["pnl_realized"]) if "pnl_realized" in executions.columns else pd.Series([], dtype="float64")
    )
    if not pnl_realized_series.empty and not executions.empty and len(side) == len(pnl_realized_series):
        realized_on_sells = pnl_realized_series[side == "SELL"]
        realized_on_sells = realized_on_sells.dropna()
    else:
        realized_on_sells = pd.Series([], dtype="float64")

    realized_win_rate = None
    profit_factor = None
    if not realized_on_sells.empty:
        gross_profit = float(realized_on_sells[realized_on_sells > 0].sum())
        gross_loss = float(realized_on_sells[realized_on_sells < 0].sum())
        realized_win_rate = float((realized_on_sells > 0).mean())
        if gross_loss < 0:
            profit_factor = gross_profit / abs(gross_loss)

    monthly_returns = pd.DataFrame(columns=["month", "return"])
    if "date" in daily.columns:
        monthly = (
            daily.set_index("date")["daily_return"]
            .resample("ME")
            .apply(lambda values: float((1.0 + values).prod() - 1.0))
            .reset_index()
        )
        monthly.columns = ["month", "return"]
        monthly_returns = monthly

    metrics = {
        "business_days": business_days,
        "final_total_asset": final_asset,
        "final_return": final_return,
        "cagr": cagr,
        "max_drawdown": mdd,
        "volatility_ann": risk_metrics["volatility_ann"],
        "downside_vol_ann": risk_metrics["downside_vol_ann"],
        "sharpe": risk_metrics["sharpe"],
        "sortino": risk_metrics["sortino"],
        "calmar": calmar,
        "var_confidence": float(var_confidence),
        "var": risk_metrics["var"],
        "cvar": risk_metrics["cvar"],
        "trades_total": int(len(executions)),
        "buy_count": int((side == "BUY").sum()) if not executions.empty else 0,
        "sell_count": int((side == "SELL").sum()) if not executions.empty else 0,
        "turnover_ratio": turnover_ratio,
        "turnover_ann": turnover_ann,
        "transaction_cost_total": transaction_cost_total,
        "avg_holding_days": compute_avg_holding_days(executions),
        "realized_win_rate": realized_win_rate,
        "profit_factor": profit_factor,
        "initial_capital": float(initial_capital),
    }
    return EvaluationResult(metrics=metrics, daily=daily, monthly_returns=monthly_returns)


def _format_pct(value: Any) -> str:
    if value is None:
        return "NA"
    return f"{float(value) * 100.0:.2f}%"


def _format_num(value: Any) -> str:
    if value is None:
        return "NA"
    return f"{float(value):,.4f}"


def build_markdown_report(label: str, metrics: dict[str, Any]) -> str:
    lines = [
        f"# Portfolio Evaluation ({label})",
        "",
        "## Core Performance",
        f"- Final return: {_format_pct(metrics.get('final_return'))}",
        f"- CAGR: {_format_pct(metrics.get('cagr'))}",
        f"- Max drawdown: {_format_pct(metrics.get('max_drawdown'))}",
        f"- Volatility (ann): {_format_pct(metrics.get('volatility_ann'))}",
        f"- Sharpe: {_format_num(metrics.get('sharpe'))}",
        f"- Sortino: {_format_num(metrics.get('sortino'))}",
        f"- Calmar: {_format_num(metrics.get('calmar'))}",
        "",
        "## Tail Risk",
        f"- VaR ({int(metrics.get('var_confidence', 0.95) * 100)}%): {_format_pct(metrics.get('var'))}",
        f"- CVaR ({int(metrics.get('var_confidence', 0.95) * 100)}%): {_format_pct(metrics.get('cvar'))}",
        "",
        "## Trade Quality",
        f"- Trades total: {int(metrics.get('trades_total') or 0)}",
        f"- Buy/Sell: {int(metrics.get('buy_count') or 0)} / {int(metrics.get('sell_count') or 0)}",
        f"- Avg holding days: {_format_num(metrics.get('avg_holding_days'))}",
        f"- Realized win rate: {_format_pct(metrics.get('realized_win_rate'))}",
        f"- Profit factor: {_format_num(metrics.get('profit_factor'))}",
        "",
        "## Turnover/Cost",
        f"- Turnover ratio: {_format_num(metrics.get('turnover_ratio'))}",
        f"- Turnover ann: {_format_num(metrics.get('turnover_ann'))}",
        f"- Transaction cost total: {_format_num(metrics.get('transaction_cost_total'))}",
    ]
    return "\n".join(lines)


def save_evaluation_artifacts(result: EvaluationResult, output_dir: Path, label: str) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "metrics.json").write_text(
        json.dumps(result.metrics, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    result.daily.to_csv(output_dir / "daily_evaluation.csv", index=False, encoding="utf-8-sig")
    result.monthly_returns.to_csv(output_dir / "monthly_returns.csv", index=False, encoding="utf-8-sig")
    (output_dir / "report.md").write_text(build_markdown_report(label=label, metrics=result.metrics), encoding="utf-8")


def _load_dataframe(csv_path: str | None) -> pd.DataFrame:
    if not csv_path:
        return pd.DataFrame()
    path = Path(csv_path).resolve()
    if not path.exists():
        raise FileNotFoundError(f"CSV file not found: {path}")
    return pd.read_csv(path)


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate daily-routine portfolio summary/execution outputs.")
    parser.add_argument("--summary_csv", type=str, required=True, help="Path to summary.csv")
    parser.add_argument("--executions_csv", type=str, default=None, help="Path to executions.csv")
    parser.add_argument("--initial_capital", type=float, default=10000.0, help="Initial capital")
    parser.add_argument("--risk_free_rate", type=float, default=0.0, help="Annual risk-free rate (e.g. 0.03)")
    parser.add_argument("--var_confidence", type=float, default=0.95, help="VaR/CVaR confidence")
    parser.add_argument("--label", type=str, default="daily_routine", help="Label written to report")
    parser.add_argument(
        "--output_dir",
        type=str,
        default=str((Path(__file__).resolve().parent / "out" / "portfolio_eval").resolve()),
        help="Output directory for metrics/report",
    )
    args = parser.parse_args()

    if not (0.0 < args.var_confidence < 1.0):
        raise ValueError("--var_confidence must be in range (0, 1).")

    summary_df = _load_dataframe(args.summary_csv)
    executions_df = _load_dataframe(args.executions_csv)

    result = evaluate_portfolio(
        summary_df=summary_df,
        executions_df=executions_df,
        initial_capital=float(args.initial_capital),
        risk_free_rate=float(args.risk_free_rate),
        var_confidence=float(args.var_confidence),
    )
    output_dir = Path(args.output_dir).resolve()
    save_evaluation_artifacts(result=result, output_dir=output_dir, label=args.label)

    print("== Portfolio Evaluation Finished ==")
    print(f"- Output: {output_dir}")
    print(f"- Final return: {_format_pct(result.metrics.get('final_return'))}")
    print(f"- Max drawdown: {_format_pct(result.metrics.get('max_drawdown'))}")
    print(f"- Sharpe: {_format_num(result.metrics.get('sharpe'))}")


if __name__ == "__main__":
    main()
