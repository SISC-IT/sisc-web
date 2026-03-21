import argparse
import os
import sys
import time
import warnings
from typing import Any, Dict, List

import pandas as pd


os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"

warnings.filterwarnings("ignore", category=UserWarning)
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", message=".*InconsistentVersionWarning.*")
warnings.filterwarnings("ignore", message=".*SQLAlchemy.*")


current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)


from AI.pipelines.daily_routine import run_daily_pipeline


class DryRunPortfolioRepository:
    def __init__(self, initial_capital: float = 10000):
        self.initial_capital = float(initial_capital)
        self.executions: List[Dict[str, Any]] = []
        self.reports: List[Dict[str, Any]] = []
        self.portfolio_summaries: Dict[str, Dict[str, float]] = {}
        self.portfolio_positions: Dict[str, List[tuple]] = {}

    def _executions_until(self, target_date: str) -> List[Dict[str, Any]]:
        return [row for row in self.executions if str(row["fill_date"]) <= target_date]

    def get_latest_total_asset(self, target_date: str, default_asset: float = 10000) -> float:
        past_dates = sorted(d for d in self.portfolio_summaries.keys() if d < target_date)
        if not past_dates:
            return float(default_asset)
        return float(self.portfolio_summaries[past_dates[-1]]["total_asset"])

    def get_current_cash(self, target_date: str = None, initial_cash: float = 10000) -> float:
        if not target_date:
            return float(initial_cash)
        past_dates = sorted(d for d in self.portfolio_summaries.keys() if d < target_date)
        if not past_dates:
            return float(initial_cash)
        return float(self.portfolio_summaries[past_dates[-1]]["cash"])

    def get_current_position(self, ticker: str, target_date: str = None, initial_cash: float = 0) -> Dict[str, Any]:
        rows = self._executions_until(target_date) if target_date else list(self.executions)
        rows = [row for row in rows if row["ticker"] == ticker]

        current_qty = 0
        avg_price = 0.0
        total_cost = 0.0
        pnl_realized_cum = 0.0

        for row in rows:
            qty = int(row["qty"])
            price = float(row["fill_price"])
            commission = float(row.get("commission", 0.0))
            side = str(row["side"]).upper()

            if side == "BUY":
                total_cost += price * qty
                current_qty += qty
                if current_qty > 0:
                    avg_price = total_cost / current_qty
            elif side == "SELL":
                pnl_realized_cum += ((price - avg_price) * qty) - commission
                current_qty -= qty
                if current_qty > 0:
                    total_cost = avg_price * current_qty
                else:
                    total_cost = 0.0
                    avg_price = 0.0

        return {
            "cash": self.get_current_cash(target_date=target_date, initial_cash=self.initial_capital),
            "qty": current_qty,
            "avg_price": avg_price,
            "pnl_realized_cum": pnl_realized_cum,
        }

    def save_executions_to_db(self, fills_df: pd.DataFrame) -> None:
        if fills_df is None or fills_df.empty:
            return
        for row in fills_df.to_dict(orient="records"):
            self.executions.append(row)
        print(f"[DryRunRepo] Captured {len(fills_df)} executions in memory.")

    def save_reports_to_db(self, reports_tuple_list: list) -> list:
        if not reports_tuple_list:
            return []
        saved_ids = []
        for idx, row in enumerate(reports_tuple_list, start=len(self.reports) + 1):
            ticker, signal, price, date, text = row
            self.reports.append(
                {"id": idx, "ticker": ticker, "signal": signal, "price": price, "date": date, "text": text}
            )
            saved_ids.append(idx)
        print(f"[DryRunRepo] Captured {len(reports_tuple_list)} XAI reports in memory.")
        return saved_ids

    def save_portfolio_summary(
        self,
        date: str,
        total_asset: float,
        cash: float,
        market_value: float,
        pnl_unrealized: float,
        pnl_realized_cum: float,
        initial_capital: float,
        return_rate: float,
    ):
        self.portfolio_summaries[date] = {
            "total_asset": float(total_asset),
            "cash": float(cash),
            "market_value": float(market_value),
            "pnl_unrealized": float(pnl_unrealized),
            "pnl_realized_cum": float(pnl_realized_cum),
            "initial_capital": float(initial_capital),
            "return_rate": float(return_rate),
        }
        print(
            f"[DryRunRepo] Summary {date}: total=${total_asset:,.0f}, cash=${cash:,.0f}, return={return_rate*100:.2f}%"
        )

    def save_portfolio_positions(self, date: str, data_tuples: list):
        self.portfolio_positions[date] = list(data_tuples)
        print(f"[DryRunRepo] Snapshot {date}: {len(data_tuples)} open positions.")


def run_dryrun_backfill(
    end_date: str,
    days: int,
    tickers: list,
    enable_xai: bool,
    sleep_seconds: float,
):
    repo = DryRunPortfolioRepository(initial_capital=10000)
    dates = pd.date_range(end=end_date, periods=days, freq="B")

    print("== Dry Run Backfill ==")
    print(f"- End date: {end_date}")
    print(f"- Business days: {len(dates)}")
    print(f"- Tickers: {tickers if tickers else 'dynamic screener'}")
    print(f"- XAI: {'ON' if enable_xai else 'OFF'}")
    print("- DB writes: skipped (in-memory repository)\n")

    for i, d in enumerate(dates, 1):
        target_date_str = d.strftime("%Y-%m-%d")
        print("\n==================================================")
        print(f"▶ [DryRun: {i}/{len(dates)}] target date: {target_date_str}")
        print("==================================================")

        try:
            run_daily_pipeline(
                target_tickers=tickers,
                mode="simulation",
                enable_xai=enable_xai,
                target_date=target_date_str,
                repo=repo,
            )
        except Exception as e:
            print(f"[DryRun] {target_date_str} failed: {e}")
            continue

        if sleep_seconds > 0:
            time.sleep(sleep_seconds)

    if repo.portfolio_summaries:
        last_date = sorted(repo.portfolio_summaries.keys())[-1]
        final_summary = repo.portfolio_summaries[last_date]
        print("\n== Dry Run Result ==")
        print(f"- Last date: {last_date}")
        print(f"- Total asset: ${final_summary['total_asset']:,.2f}")
        print(f"- Cash: ${final_summary['cash']:,.2f}")
        print(f"- Return: {final_summary['return_rate']*100:.2f}%")
        print(f"- Executions: {len(repo.executions)}")
        print(f"- Reports: {len(repo.reports)}")
    else:
        print("\n== Dry Run Result ==")
        print("- No portfolio summaries were produced.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run a DB-free dry-run backfill over recent business days.")
    parser.add_argument("--end_date", type=str, default="2026-03-21", help="End date (YYYY-MM-DD)")
    parser.add_argument("--days", type=int, default=280, help="Number of business days to simulate")
    parser.add_argument("--tickers", type=str, default="", help="Comma-separated tickers. Empty uses screener.")
    parser.add_argument("--enable_xai", action="store_true", help="Enable XAI generation")
    parser.add_argument("--sleep", type=float, default=0.0, help="Sleep seconds between days")

    args = parser.parse_args()
    ticker_list = [t.strip() for t in args.tickers.split(",") if t.strip()]

    run_dryrun_backfill(
        end_date=args.end_date,
        days=args.days,
        tickers=ticker_list,
        enable_xai=args.enable_xai,
        sleep_seconds=args.sleep,
    )
