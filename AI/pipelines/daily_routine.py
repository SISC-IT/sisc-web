"""
[일일 자동화 파이프라인 메인 오케스트레이터]
- 다중 모델 앙상블 시스템의 제어를 담당합니다.
- 세부적인 처리(데이터 프로세싱, 매매 로직 등)는 components 패키지로 위임(Delegation)되어
  단일 책임 원칙(SRP)을 준수합니다.
"""

import argparse
import os
import sys
import traceback
import warnings
from datetime import datetime
from typing import TYPE_CHECKING

import pandas as pd

# 1. TensorFlow C++ 레벨 로그 및 oneDNN 안내문 완벽 차단
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"

# 2. Pandas, Scikit-learn 등 파이썬 레벨의 경고 차단
warnings.filterwarnings("ignore", category=UserWarning)
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", message=".*InconsistentVersionWarning.*")
warnings.filterwarnings("ignore", message=".*SQLAlchemy.*")

# 프로젝트 루트 경로 추가 (시스템 경로 인식용)
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

# 모듈 Import (비즈니스 로직 및 코어)
from AI.config import TradingConfig, load_trading_config
from AI.libs.database.repository import PortfolioRepository
from AI.modules.finder.screener import DynamicScreener
from AI.modules.signal.core.data_loader import DataLoader
from AI.pipelines.components.data_processor import load_and_preprocess_data
from AI.pipelines.components.model_manager import initialize_models
from AI.pipelines.components.portfolio_logic import calculate_portfolio_allocation
from AI.pipelines.components.portfolio_settler import settle_portfolio
from AI.pipelines.components.trade_executor import execute_trades

if TYPE_CHECKING:
    from AI.modules.analysis.generator import ReportGenerator


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


def run_daily_pipeline(
    target_tickers: list[str] | None = None,
    mode: str | None = None,
    enable_xai: bool | None = None,
    xai_use_api_llm: bool = True,
    target_date: str | None = None,
    active_models: list[str] | None = None,
    repo: PortfolioRepository | None = None,
    trading_config: TradingConfig | None = None,
    config_path: str | None = None,
) -> None:
    """
    메인 루틴: 스크리너 -> 모델 초기화 -> 전처리 -> 앙상블 비중 산출 -> 주문 집행 -> 정산
    """
    trading_config = trading_config or load_trading_config(config_path)
    mode = mode or trading_config.pipeline.default_mode
    enable_xai = trading_config.pipeline.enable_xai if enable_xai is None else enable_xai

    if active_models is None:
        active_models = list(trading_config.pipeline.active_models)

    exec_date_str = target_date if target_date else datetime.now().strftime("%Y-%m-%d")
    run_id = f"daily_{exec_date_str}"
    print(f"\n[{exec_date_str}] === AI Daily Portfolio Routine (Mode: {mode.upper()}) ===")

    repo = repo if repo is not None else PortfolioRepository(db_name=trading_config.pipeline.db_name)

    if mode == "simulation" and hasattr(repo, "reset_run_data"):
        try:
            repo.reset_run_data(run_id=run_id, target_date=exec_date_str)
        except Exception as reset_error:
            print(f"[Warning] Failed to reset existing run data: {reset_error}")

    # [Step 0] 스크리닝 (특정 종목이 주어지지 않은 경우 동적 스크리닝 진행)
    if not target_tickers:
        screener = DynamicScreener(config=trading_config)
        target_tickers = screener.update_watchlist(exec_date_str)
        if not target_tickers:
            print("[DailyRoutine] Screener returned no target tickers. Stopping.")
            return

    # [Step 1] 전략 및 피처 설정
    feature_columns = list(trading_config.data.feature_columns)

    # [Step 2] 다중 모델 객체 로드 및 초기화
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
        run_mode=mode,
    )
    if not model_wrappers:
        print("[DailyRoutine] No active models were initialized. Stopping.")
        return

    # [Step 3] 데이터 조회 및 전처리 수행
    data_map = load_and_preprocess_data(
        loader=loader,
        target_tickers=target_tickers,
        exec_date_str=exec_date_str,
        pipeline_config=trading_config.pipeline,
        data_config=trading_config.data,
        model_wrappers=model_wrappers,
    )
    if not data_map:
        print("[DailyRoutine] No valid data was available after preprocessing. Stopping.")
        return

    # LLM 호출 실패(API 키 없음 등)가 전체 매매 파이프라인을 중단시키지 않도록 격리합니다.
    xai_generator = None
    if enable_xai:
        try:
            from AI.modules.analysis.generator import ReportGenerator

            xai_generator = ReportGenerator(use_api_llm=xai_use_api_llm)
        except Exception as xai_error:
            print(f"[Warning] XAI initialization failed. Continuing without reports: {xai_error}")
            xai_generator = None

    # [Step 4] 포트폴리오 비중 계산
    print("4. Calculating target portfolio weights...")
    dummy_macro_data = _build_default_macro_frame(trading_config)

    try:
        target_weights, scores, _ = calculate_portfolio_allocation(
            data_map=data_map,
            macro_data=dummy_macro_data,
            model_wrappers=model_wrappers,
            ticker_ids=loader.ticker_to_id,
            ticker_to_sector_id=loader.ticker_to_sector_id,
            gating_model=None,
            data_config=trading_config.data,
            portfolio_config=trading_config.portfolio,
        )
    except Exception as e:
        print(f"[DailyRoutine] Portfolio allocation failed: {e}")
        traceback.print_exc()
        return

    # [Step 5] 주문 실행 및 리포트 생성
    execution_results, report_results, current_cash = execute_trades(
        repo=repo,
        target_tickers=target_tickers,
        data_map=data_map,
        target_weights=target_weights,
        scores=scores,
        exec_date_str=exec_date_str,
        mode=mode,
        enable_xai=enable_xai,
        xai_generator=xai_generator,
        pipeline_config=trading_config.pipeline,
        portfolio_config=trading_config.portfolio,
        execution_config=trading_config.execution,
    )

    # [Step 6] 결과 데이터베이스 일괄 저장 (DB Transaction)
    saved_report_map = {}
    if report_results:
        print(f"6-1. Saving XAI reports ({len(report_results)})...")
        reports_tuple = [
            (report["ticker"], report["signal"], float(report["price"]), report["date"], report["text"])
            for report in report_results
        ]
        try:
            # run_id is used so reruns can replace same-day XAI artifacts.
            saved_report_ids = repo.save_reports_to_db(reports_tuple, run_id=run_id)
            saved_report_map = {
                report["ticker"]: saved_id
                for report, saved_id in zip(report_results, saved_report_ids)
            }
        except Exception as db_error:
            print(f"   [Error] Failed to save reports: {db_error}")

    for execution in execution_results:
        if execution["ticker"] in saved_report_map:
            execution["xai_report_id"] = saved_report_map[execution["ticker"]]

    if execution_results:
        print(f"6-2. Saving executions ({len(execution_results)})...")
        try:
            repo.save_executions_to_db(pd.DataFrame(execution_results))
        except Exception as db_error:
            print(f"   [Error] Failed to save executions: {db_error}")
            return

    # [Step 7] 일일 포트폴리오 스냅샷 정산
    settle_portfolio(
        repo=repo,
        target_tickers=target_tickers,
        data_map=data_map,
        exec_date_str=exec_date_str,
        pipeline_config=trading_config.pipeline,
        current_cash=current_cash,
    )

    print("=== Daily Routine Finished ===\n")


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

    parser = argparse.ArgumentParser(parents=[bootstrap_parser])
    parser.add_argument("--tickers", type=str, default="", help="Comma-separated tickers. Empty uses the screener.")
    parser.add_argument(
        "--mode",
        type=str,
        default=trading_config.pipeline.default_mode,
        choices=["simulation", "live"],
        help="Execution mode.",
    )
    xai_group = parser.add_mutually_exclusive_group()
    xai_group.add_argument("--xai", dest="enable_xai", action="store_true", help="Enable XAI report generation.")
    xai_group.add_argument(
        "--no-xai",
        dest="enable_xai",
        action="store_false",
        help="Disable XAI report generation.",
    )
    parser.set_defaults(enable_xai=trading_config.pipeline.enable_xai)
    parser.add_argument("--target_date", type=str, default=None, help="Run date in YYYY-MM-DD format.")
    parser.add_argument(
        "--models",
        type=str,
        default=",".join(trading_config.pipeline.active_models),
        help="Comma-separated model list.",
    )

    args = parser.parse_args()

    ticker_list = [ticker.strip() for ticker in args.tickers.split(",") if ticker.strip()]
    model_list = [model.strip().lower() for model in args.models.split(",") if model.strip()]
    if not model_list:
        parser.error("At least one model must be supplied. Example: --models transformer")

    run_daily_pipeline(
        target_tickers=ticker_list,
        mode=args.mode,
        enable_xai=args.enable_xai,
        target_date=args.target_date,
        active_models=model_list,
        trading_config=trading_config,
    )
