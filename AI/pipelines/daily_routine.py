#AI/pipelines/daily_routine.py
"""
[일일 자동화 파이프라인 메인 오케스트레이터]
- 다중 모델 앙상블 시스템의 제어를 담당합니다.
- 세부적인 처리(데이터 프로세싱, 매매 로직 등)는 components 패키지로 위임(Delegation)되어
  단일 책임 원칙(SRP)을 준수합니다.
"""

import os
import warnings

# 1. TensorFlow C++ 레벨 로그 및 oneDNN 안내문 완벽 차단
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3' 
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'

# 2. Pandas, Scikit-learn 등 파이썬 레벨의 경고 차단
warnings.filterwarnings('ignore', category=UserWarning)
warnings.filterwarnings('ignore', category=FutureWarning)
warnings.filterwarnings('ignore', message='.*InconsistentVersionWarning.*')
warnings.filterwarnings('ignore', message='.*SQLAlchemy.*')

import sys
import argparse
import traceback
import pandas as pd
from datetime import datetime

# 프로젝트 루트 경로 추가 (시스템 경로 인식용)
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

# 모듈 Import (비즈니스 로직 및 코어)
from AI.libs.database.repository import PortfolioRepository
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.finder.screener import DynamicScreener
from AI.pipelines.components.portfolio_logic import calculate_portfolio_allocation
from AI.modules.analysis.generator import ReportGenerator

from AI.pipelines.components.model_manager import initialize_models
from AI.pipelines.components.data_processor import load_and_preprocess_data
from AI.pipelines.components.trade_executor import execute_trades
from AI.pipelines.components.portfolio_settler import settle_portfolio


def run_daily_pipeline(target_tickers: list = None, mode: str = "simulation", 
                       enable_xai: bool = True, target_date: str = None, active_models: list = None,
                       repo: PortfolioRepository = None):
    """
    메인 루틴: 스크리너 -> 모델 초기화 -> 전처리 -> 앙상블 비중 산출 -> 주문 집행 -> 정산
    """
    if active_models is None:
        active_models = ["transformer"]

    exec_date_str = target_date if target_date else datetime.now().strftime("%Y-%m-%d")
    print(f"\n[{exec_date_str}] === AI Daily Portfolio Routine (Mode: {mode.upper()}) ===")
    
    repo = repo if repo is not None else PortfolioRepository(db_name="db")

    # [Step 0] 스크리닝 (특정 종목이 주어지지 않은 경우 동적 스크리닝 진행)
    if not target_tickers:
        screener = DynamicScreener()
        target_tickers = screener.update_watchlist(exec_date_str, top_n=30)
        if not target_tickers:
            print("⚠️ 스크리닝 결과가 없어 루틴을 종료합니다.")
            return

    # [Step 1] 전략 및 피처 설정
    strategy_config = {"seq_len": 60, "top_k": 3, "buy_threshold": 0.70}
    feature_columns = []

    # [Step 2] 다중 모델 객체 로드 및 초기화
    loader = DataLoader()
    model_wrappers = initialize_models(loader, strategy_config, feature_columns, active_models)
    if not model_wrappers:
        print("⚠️ 활성화된 모델이 없습니다. 루틴을 종료합니다.")
        return

    # [Step 3] 데이터 조회 및 전처리 수행
    data_map = load_and_preprocess_data(
        loader,
        target_tickers,
        exec_date_str,
        strategy_config,
        model_wrappers,
    )
    if not data_map:
        print("⚠️ 오늘(해당일) 처리할 수 있는 정상 데이터가 없습니다. 루틴을 종료합니다.")
        return

    # LLM 호출 실패(API 키 없음 등)가 전체 매매 파이프라인을 중단시키지 않도록 격리합니다.
    xai_generator = None
    if enable_xai:
        try:
            xai_generator = ReportGenerator(use_api_llm=True)
        except Exception as xai_e:
            print(f"⚠️ [경고] XAI 초기화 실패 (API 연동 에러 등). XAI 없이 매매 루틴을 계속 진행합니다. 사유: {xai_e}")
            xai_generator = None

    # [Step 4] 포트폴리오 비중 계산
    print("4. AI 앙상블 포트폴리오 전략 산출 중...")
    dummy_macro_data = pd.DataFrame([{"vix_z_score": 0.0, "mkt_breadth_nh_nl": 0.0, "ma_trend_score": 0.5}])
    
    try:
        target_weights, scores, all_signals_map = calculate_portfolio_allocation(
            data_map=data_map, macro_data=dummy_macro_data, model_wrappers=model_wrappers,
            ticker_ids=loader.ticker_to_id, ticker_to_sector_id=loader.ticker_to_sector_id, 
            gating_model=None, config=strategy_config
        )
    except Exception as e:
        print(f"❌ 포트폴리오 산출 중 치명적 오류 발생: {e}")
        traceback.print_exc()
        return
    
    execution_results, report_results = execute_trades(
        repo=repo, 
        target_tickers=target_tickers, 
        data_map=data_map, 
        target_weights=target_weights, 
        scores=scores, 
        exec_date_str=exec_date_str, 
        mode=mode,  # <-- CLI에서 받은 실행 모드를 하위 컴포넌트로 전달
        enable_xai=enable_xai, 
        xai_generator=xai_generator
    )

    # [Step 6] 결과 데이터베이스 일괄 저장 (DB Transaction)
    saved_report_map = {}
    if report_results:
        print(f"6-1. XAI 리포트 DB 저장 중... ({len(report_results)}건)")
        reports_tuple = [(r["ticker"], r["signal"], float(r["price"]), r["date"], r["text"]) for r in report_results]
        try:
            saved_report_ids = repo.save_reports_to_db(reports_tuple)
            saved_report_map = {r["ticker"]: saved_id for r, saved_id in zip(report_results, saved_report_ids)}
        except Exception as db_e:
            print(f"   [Error] 리포트 저장 실패: {db_e}")

    for exe in execution_results:
        if exe['ticker'] in saved_report_map:
            exe['xai_report_id'] = saved_report_map[exe['ticker']]

    if execution_results:
        print(f"6-2. 매매 실행 내역 DB 저장 중... ({len(execution_results)}건)")
        try:
            repo.save_executions_to_db(pd.DataFrame(execution_results))
        except Exception as db_e:
            print(f"   [Error] 실행 내역 저장 실패: {db_e}")
            return

    settle_portfolio(repo, target_tickers, data_map, exec_date_str)
    
    print("=== Daily Routine Finished ===\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tickers", type=str, default="", help="특정 종목 테스트용 (생략 시 다이나믹 스크리닝 자동 실행)")
    parser.add_argument("--mode", type=str, default="simulation", choices=["simulation", "live"], help="실행 모드")
    parser.add_argument("--no-xai", action="store_true", help="XAI 리포트 생성 건너뛰기")
    parser.add_argument("--target_date", type=str, default=None, help="과거 시뮬레이션 기준 날짜 (YYYY-MM-DD)")
    parser.add_argument("--models", type=str, default="transformer", help="사용할 모델 리스트 (콤마로 구분, 예: transformer,patchtst)")
    
    args = parser.parse_args()
    
    ticker_list = [t.strip() for t in args.tickers.split(",") if t.strip()] if args.tickers else []
    model_list = [m.strip().lower() for m in args.models.split(",") if m.strip()]
    
    # 모델 리스트가 아예 비어있으면 실행 전 차단
    if not model_list:
        parser.error("유효한 모델 이름이 입력되지 않았습니다. (예: --models transformer)")
    
    run_daily_pipeline(
        target_tickers=ticker_list, 
        mode=args.mode, 
        enable_xai=not args.no_xai, 
        target_date=args.target_date,
        active_models=model_list
    )
