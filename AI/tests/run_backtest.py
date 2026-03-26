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

from AI.config import TradingConfig, load_trading_config


class DryRunPortfolioRepository:
    def __init__(self, initial_capital: float):
        self.initial_capital = float(initial_capital)
        self.executions: List[Dict[str, Any]] = []
        self.reports: List[Dict[str, Any]] = []
        self.portfolio_summaries: Dict[str, Dict[str, float]] = {}
        self.portfolio_positions: Dict[str, List[tuple]] = {}

    def _executions_until(self, target_date: str) -> List[Dict[str, Any]]:
        return [row for row in self.executions if str(row["fill_date"]) <= target_date]

    def get_latest_total_asset(self, target_date: str, default_asset: float = 10000) -> float:
        past_dates = sorted(date for date in self.portfolio_summaries.keys() if date < target_date)
        if not past_dates:
            return float(default_asset)
        return float(self.portfolio_summaries[past_dates[-1]]["total_asset"])

    def get_current_cash(self, target_date: str = None, initial_cash: float = 10000) -> float:
        if not target_date:
            return float(initial_cash)
        past_dates = sorted(date for date in self.portfolio_summaries.keys() if date < target_date)
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

    def get_open_tickers(self, target_date: str) -> List[str]:
        rows = self._executions_until(target_date)
        qty_map: Dict[str, int] = {}
        for row in rows:
            ticker = str(row["ticker"])
            side = str(row["side"]).upper()
            qty = int(row["qty"])
            delta = qty if side == "BUY" else -qty
            qty_map[ticker] = qty_map.get(ticker, 0) + delta
        return sorted([ticker for ticker, qty in qty_map.items() if qty > 0])

    def reset_run_data(self, run_id: str, target_date: str | None = None) -> None:
        if not run_id:
            return
        self.executions = [row for row in self.executions if str(row.get("run_id")) != run_id]
        self.reports = [row for row in self.reports if str(row.get("run_id")) != run_id]
        if target_date:
            self.portfolio_summaries.pop(target_date, None)
            self.portfolio_positions.pop(target_date, None)

    def save_executions_to_db(self, fills_df: pd.DataFrame) -> None:
        if fills_df is None or fills_df.empty:
            return
        run_ids = {
            str(run_id).strip()
            for run_id in fills_df["run_id"].tolist()
            if pd.notna(run_id) and str(run_id).strip()
        }
        if run_ids:
            self.executions = [
                row for row in self.executions if str(row.get("run_id")).strip() not in run_ids
            ]
        for row in fills_df.to_dict(orient="records"):
            self.executions.append(row)
        print(f"[DryRunRepo] Captured {len(fills_df)} executions in memory.")

    def save_reports_to_db(self, reports_tuple_list: list, run_id: str | None = None) -> list:
        if not reports_tuple_list:
            return []
        saved_ids: List[int] = []
        for row in reports_tuple_list:
            ticker, signal, price, date, text = row
            existing = next(
                (
                    item
                    for item in self.reports
                    if item["ticker"] == ticker and item["signal"] == signal and item["date"] == date
                ),
                None,
            )
            if existing is not None:
                existing.update({"price": price, "text": text, "run_id": run_id})
                saved_ids.append(int(existing["id"]))
                continue

            new_id = len(self.reports) + 1
            self.reports.append(
                {
                    "id": new_id,
                    "ticker": ticker,
                    "signal": signal,
                    "price": price,
                    "date": date,
                    "text": text,
                    "run_id": run_id,
                }
            )
            saved_ids.append(new_id)
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


def _build_default_macro_frame(trading_config: TradingConfig) -> pd.DataFrame:
    fallback = trading_config.pipeline.macro_fallback
    return pd.DataFrame(
        [
            {
                "vix_z_score": fallback.vix_z_score,
                "mkt_breadth_nh_nl": fallback.mkt_breadth_nh_nl,
                "ma_trend_score": fallback.ma_trend_score,
            }
        ]
    )


def _build_daily_ticker_plan(
    dates: pd.DatetimeIndex,
    static_tickers: list[str],
    trading_config: TradingConfig,
    screener_mode: str,
) -> tuple[dict[str, list[str]], list[str]]:
    if static_tickers:
        normalized = [ticker.strip() for ticker in static_tickers if ticker.strip()]
        unique_tickers = list(dict.fromkeys(normalized))
        day_plan = {date.strftime("%Y-%m-%d"): list(unique_tickers) for date in dates}
        return day_plan, unique_tickers

    from AI.modules.finder.screener import DynamicScreener

    screener = DynamicScreener(config=trading_config)
    day_plan: dict[str, list[str]] = {}
    if screener_mode == "once":
        base_day = dates[0].strftime("%Y-%m-%d")
        base_tickers = screener.update_watchlist(base_day)
        cleaned = [ticker.strip() for ticker in base_tickers if ticker and ticker.strip()]
        unique_tickers = list(dict.fromkeys(cleaned))
        for target_date in dates:
            day_plan[target_date.strftime("%Y-%m-%d")] = list(unique_tickers)
        print(f"[Backtest] Screener mode=once | base day: {base_day} | tickers: {len(unique_tickers)}")
        return day_plan, unique_tickers

    universe: list[str] = []
    seen: set[str] = set()
    for target_date in dates:
        target_date_str = target_date.strftime("%Y-%m-%d")
        daily_tickers = screener.update_watchlist(target_date_str)
        cleaned = [ticker.strip() for ticker in daily_tickers if ticker and ticker.strip()]
        day_plan[target_date_str] = cleaned
        for ticker in cleaned:
            if ticker not in seen:
                seen.add(ticker)
                universe.append(ticker)

    print(f"[Backtest] Screener mode=daily | union tickers: {len(universe)}")
    return day_plan, universe


def _slice_data_for_date(
    preprocessed_data_map: dict[str, pd.DataFrame],
    target_tickers: list[str],
    target_timestamp: pd.Timestamp,
    minimum_history_length: int,
) -> dict[str, pd.DataFrame]:
    sliced: dict[str, pd.DataFrame] = {}
    for ticker in target_tickers:
        ticker_df = preprocessed_data_map.get(ticker)
        if ticker_df is None or ticker_df.empty:
            continue
        daily_df = ticker_df.loc[:target_timestamp]
        if daily_df.empty:
            continue
        if daily_df.index[-1] != target_timestamp:
            continue
        if len(daily_df) < minimum_history_length:
            continue
        sliced[ticker] = daily_df
    return sliced


def _run_dryrun_backfill_legacy(
    start_day: str,
    end_day: str,
    tickers: list[str],
    enable_xai: bool,
    sleep_seconds: float,
    trading_config: TradingConfig,
    screener_mode: str,
) -> DryRunPortfolioRepository:
    from AI.pipelines.daily_routine import run_daily_pipeline
    from AI.modules.finder.screener import DynamicScreener

    repo = DryRunPortfolioRepository(initial_capital=trading_config.pipeline.initial_capital)
    dates = pd.date_range(start=start_day, end=end_day, freq="B")
    resolved_tickers = list(tickers)
    if not resolved_tickers and screener_mode == "once" and len(dates) > 0:
        base_day = dates[0].strftime("%Y-%m-%d")
        screener = DynamicScreener(config=trading_config)
        resolved_tickers = [ticker.strip() for ticker in screener.update_watchlist(base_day) if ticker and ticker.strip()]
        resolved_tickers = list(dict.fromkeys(resolved_tickers))
        print(f"[Legacy] Screener mode=once | base day: {base_day} | tickers: {len(resolved_tickers)}")

    print("== Backtest Start ==")
    print("- Engine: legacy (daily pipeline rerun)")
    print(f"- Date range: {start_day} ~ {end_day}")
    print(f"- Business days: {len(dates)}")
    print(f"- Tickers: {resolved_tickers if resolved_tickers else 'dynamic screener (daily)'}")
    print(f"- XAI: {'ON' if enable_xai else 'OFF'}")
    if enable_xai:
        print("- XAI LLM: local ollama (OLLAMA_MODEL or first installed model)")
    print("- DB writes: skipped (in-memory repository)\n")

    for index, target_date in enumerate(dates, start=1):
        target_date_str = target_date.strftime("%Y-%m-%d")
        print("\n==================================================")
        print(f"[Backtest-Legacy: {index}/{len(dates)}] target date: {target_date_str}")
        print("==================================================")

        try:
            run_daily_pipeline(
                target_tickers=resolved_tickers,
                mode="simulation",
                enable_xai=enable_xai,
                xai_use_api_llm=False,
                target_date=target_date_str,
                repo=repo,
                trading_config=trading_config,
            )
        except Exception as e:
            print(f"[Backtest-Legacy] {target_date_str} failed: {e}")
            continue

        if sleep_seconds > 0:
            time.sleep(sleep_seconds)

    return repo


def _run_dryrun_backfill_backtest(
    start_day: str,
    end_day: str,
    tickers: list[str],
    enable_xai: bool,
    sleep_seconds: float,
    trading_config: TradingConfig,
    active_models: list[str],
    screener_mode: str,
) -> DryRunPortfolioRepository:
    from AI.modules.signal.core.data_loader import DataLoader
    from AI.pipelines.components.data_processor import load_and_preprocess_data
    from AI.pipelines.components.model_manager import initialize_models
    from AI.pipelines.components.portfolio_logic import calculate_portfolio_allocation
    from AI.pipelines.components.portfolio_settler import settle_portfolio
    from AI.pipelines.components.trade_executor import execute_trades

    repo = DryRunPortfolioRepository(initial_capital=trading_config.pipeline.initial_capital)
    dates = pd.date_range(start=start_day, end=end_day, freq="B")

    if dates.empty:
        print("[Backtest] No business dates were generated.")
        return repo

    first_business_day = dates[0].strftime("%Y-%m-%d")
    last_business_day = dates[-1].strftime("%Y-%m-%d")

    print("== Backtest Start ==")
    print("- Engine: backtest (shared model/data cache)")
    print(f"- Date range: {first_business_day} ~ {last_business_day}")
    print(f"- Business days: {len(dates)}")
    if tickers:
        ticker_info = str(tickers)
    else:
        ticker_info = f"dynamic screener ({screener_mode})"
    print(f"- Tickers: {ticker_info}")
    print(f"- Models: {active_models}")
    print(f"- XAI: {'ON' if enable_xai else 'OFF'}")
    if enable_xai:
        print("- XAI LLM: local ollama (OLLAMA_MODEL or first installed model)")
    print("- DB writes: skipped (in-memory repository)\n")

    day_ticker_plan, ticker_universe = _build_daily_ticker_plan(
        dates=dates,
        static_tickers=tickers,
        trading_config=trading_config,
        screener_mode=screener_mode,
    )
    if not ticker_universe:
        print("[Backtest] No tickers available after planning. Stopping.")
        return repo

    feature_columns = list(trading_config.data.feature_columns)
    loader = DataLoader(
        db_name=trading_config.pipeline.db_name,
        lookback=trading_config.data.seq_len,
        horizons=list(trading_config.data.prediction_horizons),
    )
    model_wrappers = initialize_models(
        loader=loader,
        data_config=trading_config.data,
        model_config=trading_config.model,
        feature_columns=feature_columns,
        active_models=active_models,
    )
    if not model_wrappers:
        print("[Backtest] No active models were initialized. Stopping.")
        return repo

    print(f"[Backtest] Preprocessing ticker universe once: {len(ticker_universe)} tickers")
    preprocessed_data_map = load_and_preprocess_data(
        loader=loader,
        target_tickers=ticker_universe,
        exec_date_str=end_day,
        pipeline_config=trading_config.pipeline,
        data_config=trading_config.data,
        model_wrappers=model_wrappers,
        allow_backward_fill=False,
    )
    if not preprocessed_data_map:
        print("[Backtest] No valid data available after preprocessing. Stopping.")
        return repo

    xai_generator = None
    if enable_xai:
        try:
            from AI.modules.analysis.generator import ReportGenerator

            xai_generator = ReportGenerator(use_api_llm=False)
        except Exception as xai_error:
            print(f"[Warning] XAI initialization failed. Continuing without reports: {xai_error}")
            xai_generator = None

    minimum_history_length = max(trading_config.data.seq_len, trading_config.data.minimum_history_length)
    dummy_macro_data = _build_default_macro_frame(trading_config)

    for index, target_date in enumerate(dates, start=1):
        target_date_str = target_date.strftime("%Y-%m-%d")
        run_id = f"daily_{target_date_str}"

        print("\n==================================================")
        print(f"[Backtest: {index}/{len(dates)}] target date: {target_date_str}")
        print("==================================================")

        try:
            repo.reset_run_data(run_id=run_id, target_date=target_date_str)
        except Exception as reset_error:
            print(f"[Warning] Failed to reset existing run data: {reset_error}")

        target_tickers = list(dict.fromkeys(day_ticker_plan.get(target_date_str, tickers)))
        open_tickers = repo.get_open_tickers(target_date_str)
        managed_tickers = list(dict.fromkeys(target_tickers + open_tickers))
        if not managed_tickers:
            print("[Backtest] No tickers for this date. Skipping.")
            continue

        data_map = _slice_data_for_date(
            preprocessed_data_map=preprocessed_data_map,
            target_tickers=managed_tickers,
            target_timestamp=target_date,
            minimum_history_length=minimum_history_length,
        )
        if not data_map:
            print("[Backtest] No valid preprocessed rows for this date. Skipping.")
            continue

        alloc_data_map = {ticker: data_map[ticker] for ticker in target_tickers if ticker in data_map}
        if alloc_data_map:
            try:
                target_weights, scores, _ = calculate_portfolio_allocation(
                    data_map=alloc_data_map,
                    macro_data=dummy_macro_data,
                    model_wrappers=model_wrappers,
                    ticker_ids=loader.ticker_to_id,
                    ticker_to_sector_id=loader.ticker_to_sector_id,
                    gating_model=None,
                    data_config=trading_config.data,
                    portfolio_config=trading_config.portfolio,
                )
            except Exception as alloc_error:
                print(f"[Backtest] Portfolio allocation failed: {alloc_error}")
                continue
        else:
            print("[Backtest] No current screener tickers had valid data. Managing open positions only.")
            target_weights, scores = {}, {}

        execution_results, report_results, current_cash = execute_trades(
            repo=repo,
            target_tickers=target_tickers,
            data_map=data_map,
            target_weights=target_weights,
            scores=scores,
            exec_date_str=target_date_str,
            mode="simulation",
            enable_xai=enable_xai,
            xai_generator=xai_generator,
            pipeline_config=trading_config.pipeline,
            portfolio_config=trading_config.portfolio,
            execution_config=trading_config.execution,
        )

        saved_report_map: dict[str, int] = {}
        if report_results:
            reports_tuple = [
                (report["ticker"], report["signal"], float(report["price"]), report["date"], report["text"])
                for report in report_results
            ]
            try:
                saved_report_ids = repo.save_reports_to_db(reports_tuple, run_id=run_id)
                saved_report_map = {
                    report["ticker"]: saved_id for report, saved_id in zip(report_results, saved_report_ids)
                }
            except Exception as db_error:
                print(f"   [Error] Failed to save reports: {db_error}")

        for execution in execution_results:
            if execution["ticker"] in saved_report_map:
                execution["xai_report_id"] = saved_report_map[execution["ticker"]]

        if execution_results:
            try:
                repo.save_executions_to_db(pd.DataFrame(execution_results))
            except Exception as db_error:
                print(f"   [Error] Failed to save executions: {db_error}")
                continue

        settle_portfolio(
            repo=repo,
            target_tickers=target_tickers,
            data_map=data_map,
            exec_date_str=target_date_str,
            pipeline_config=trading_config.pipeline,
            current_cash=current_cash,
        )

        if sleep_seconds > 0:
            time.sleep(sleep_seconds)

    return repo


def run_backtest(
    start_day: str,
    end_day: str,
    tickers: list[str],
    enable_xai: bool,
    sleep_seconds: float,
    trading_config: TradingConfig,
    engine: str = "backtest",
    active_models: list[str] | None = None,
    screener_mode: str = "once",
) -> None:
    selected_models = active_models if active_models is not None else list(trading_config.pipeline.active_models)

    if engine == "legacy":
        repo = _run_dryrun_backfill_legacy(
            start_day=start_day,
            end_day=end_day,
            tickers=tickers,
            enable_xai=enable_xai,
            sleep_seconds=sleep_seconds,
            trading_config=trading_config,
            screener_mode=screener_mode,
        )
    else:
        repo = _run_dryrun_backfill_backtest(
            start_day=start_day,
            end_day=end_day,
            tickers=tickers,
            enable_xai=enable_xai,
            sleep_seconds=sleep_seconds,
            trading_config=trading_config,
            active_models=selected_models,
            screener_mode=screener_mode,
        )

    if repo.portfolio_summaries:
        last_date = sorted(repo.portfolio_summaries.keys())[-1]
        final_summary = repo.portfolio_summaries[last_date]
        print("\n== Backtest Result ==")
        print(f"- Last date: {last_date}")
        print(f"- Total asset: ${final_summary['total_asset']:,.2f}")
        print(f"- Cash: ${final_summary['cash']:,.2f}")
        print(f"- Return: {final_summary['return_rate']*100:.2f}%")
        print(f"- Executions: {len(repo.executions)}")
        print(f"- Reports: {len(repo.reports)}")
    else:
        print("\n== Backtest Result ==")
        print("- No portfolio summaries were produced.")


# Backward-compat alias for existing imports.
run_dryrun_backfill = run_backtest


def main() -> None:
    bootstrap_parser = argparse.ArgumentParser(add_help=False)
    bootstrap_parser.add_argument(
        "--config",
        type=str,
        default=None,
        help="Optional path to a trading config override JSON file.",
    )
    bootstrap_args, _ = bootstrap_parser.parse_known_args()
    trading_config = load_trading_config(bootstrap_args.config)

    parser = argparse.ArgumentParser(
        parents=[bootstrap_parser],
        description="Run backtest over business days.",
    )
    parser.add_argument("--start_day", type=str, default="2025-03-03", help="Start day (YYYY-MM-DD)")
    parser.add_argument("--end_day", type=str, default="2026-03-24", help="End day (YYYY-MM-DD)")
    parser.add_argument(
        "--engine",
        type=str,
        default="backtest",
        choices=["backtest", "legacy"],
        help="backtest=shared model/data cache (faster), legacy=daily pipeline rerun",
    )
    parser.add_argument(
        "--models",
        type=str,
        default=",".join(trading_config.pipeline.active_models),
        help="Comma-separated model list for backtest engine.",
    )
    parser.add_argument(
        "--screener_mode",
        type=str,
        default="once",
        choices=["once", "daily"],
        help="Applied when --tickers is empty. once=faster, daily=original behavior.",
    )
    parser.add_argument("--tickers", type=str, default="", help="Comma-separated tickers. Empty uses screener.")
    xai_group = parser.add_mutually_exclusive_group()
    xai_group.add_argument("--xai", dest="enable_xai", action="store_true", help="Enable XAI generation")
    xai_group.add_argument("--no-xai", dest="enable_xai", action="store_false", help="Disable XAI generation (default)")
    parser.set_defaults(enable_xai=False)
    parser.add_argument("--sleep", type=float, default=0.0, help="Sleep seconds between days")

    args = parser.parse_args()
    ticker_list = [ticker.strip() for ticker in args.tickers.split(",") if ticker.strip()]
    model_list = [model.strip().lower() for model in args.models.split(",") if model.strip()]
    if not model_list:
        parser.error("At least one model must be supplied. Example: --models transformer")
    try:
        parsed_start_day = pd.to_datetime(args.start_day)
        parsed_end_day = pd.to_datetime(args.end_day)
    except Exception:
        parser.error("Invalid date format. Use YYYY-MM-DD for --start_day/--end_day.")
    if parsed_start_day > parsed_end_day:
        parser.error("--start_day must be less than or equal to --end_day.")

    run_backtest(
        start_day=args.start_day,
        end_day=args.end_day,
        tickers=ticker_list,
        enable_xai=args.enable_xai,
        sleep_seconds=args.sleep,
        trading_config=trading_config,
        engine=args.engine,
        active_models=model_list,
        screener_mode=args.screener_mode,
    )


if __name__ == "__main__":
    main()
