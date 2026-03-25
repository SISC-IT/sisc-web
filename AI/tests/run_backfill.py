#AI/tests/run_backfill.py
"""
Backfill runner for the daily trading pipeline.
"""

import argparse
import os
import sys
import time
import warnings

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
from AI.pipelines.daily_routine import run_daily_pipeline


def run_backfill(
    start_date: str,
    end_date: str,
    tickers: list[str],
    enable_xai: bool,
    trading_config: TradingConfig,
) -> None:
    print("== Backfill Start ==")
    print(f"- Date range: {start_date} ~ {end_date}")
    print(f"- Tickers: {tickers if tickers else 'dynamic screener'}")
    print(f"- XAI: {'ON' if enable_xai else 'OFF'}\n")
    if enable_xai:
        print("- XAI LLM: local ollama (llama3-ko)")

    dates = pd.date_range(start=start_date, end=end_date, freq="B")

    for index, target_date in enumerate(dates, start=1):
        target_date_str = target_date.strftime("%Y-%m-%d")
        print("\n==================================================")
        print(f"[Progress: {index}/{len(dates)}] target date: {target_date_str}")
        print("==================================================")

        try:
            run_daily_pipeline(
                target_tickers=tickers,
                mode="simulation",
                enable_xai=enable_xai,
                xai_use_api_llm=False,
                target_date=target_date_str,
                trading_config=trading_config,
            )
            time.sleep(1)
        except Exception as e:
            print(f"[Backfill] {target_date_str} failed: {e}")
            continue

    print("\n== Backfill Finished ==")


if __name__ == "__main__":
    bootstrap_parser = argparse.ArgumentParser(add_help=False)
    bootstrap_parser.add_argument(
        "--config",
        type=str,
        default=None,
        help="Optional path to a trading config override JSON file.",
    )
    bootstrap_args, _ = bootstrap_parser.parse_known_args()
    trading_config = load_trading_config(bootstrap_args.config)

    parser = argparse.ArgumentParser(parents=[bootstrap_parser], description="Run backfill over business days.")
    parser.add_argument("--start_date", type=str, default="2026-03-24", help="Start date (YYYY-MM-DD)")
    parser.add_argument("--end_date", type=str, default="2026-03-24", help="End date (YYYY-MM-DD)")
    xai_group = parser.add_mutually_exclusive_group()
    xai_group.add_argument("--xai", dest="enable_xai", action="store_true", help="Enable XAI generation")
    xai_group.add_argument("--no-xai", dest="enable_xai", action="store_false", help="Disable XAI generation")
    parser.set_defaults(enable_xai=trading_config.pipeline.enable_xai)

    args = parser.parse_args()

    run_backfill(
        start_date=args.start_date,
        end_date=args.end_date,
        tickers=[],
        enable_xai=args.enable_xai,
        trading_config=trading_config,
    )
