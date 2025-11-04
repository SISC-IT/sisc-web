import os
import sys
from typing import List, Dict, Optional
from datetime import datetime, timedelta, timezone
import pandas as pd

# --- 프로젝트 루트 경로 설정 ---
project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(project_root)
# ------------------------------

# --- 모듈 import ---
from finder.main import run_finder
from transformer.main import run_transformer
from libs.utils.fetch_ohlcv import fetch_ohlcv
from xai.run_xai import run_xai
from libs.utils.get_db_conn import get_db_conn
from libs.utils.save_reports_to_db import save_reports_to_db
# ---------------------------------

# DB 이름 상수(실제 등록된 키와 반드시 일치해야 함)
MARKET_DB_NAME = "db"          # 시세/원천 데이터 DB
REPORT_DB_NAME = "report_DB"   # 리포트 저장 DB

REQUIRED_LOG_COLS = {
    "ticker", "date", "action", "price",
    "feature1", "feature2", "feature3",
    "prob1", "prob2", "prob3"
}

def run_weekly_finder() -> List[str]:
    """
    주간 종목 발굴(Finder)을 실행하고 결과(종목 리스트)를 반환합니다.
    """
    print("--- [PIPELINE-STEP 1] Finder 모듈 실행 시작 ---")
    # top_tickers = run_finder()
    top_tickers = ["AAPL", "MSFT", "GOOGL"]  # 임시 데이터
    print("--- [PIPELINE-STEP 1] Finder 모듈 실행 완료 ---")
    return top_tickers

def _utcnow() -> datetime:
    return datetime.now(timezone.utc)

def run_signal_transformer(tickers: List[str], db_name: str) -> pd.DataFrame:
    """
    종목 리스트를 받아 Transformer 모듈을 실행하고, 신호(결정 로그)를 반환합니다.
    """
    print("--- [PIPELINE-STEP 2] Transformer 모듈 실행 시작 ---")

    if not tickers:
        print("[WARN] 빈 종목 리스트가 입력되어 Transformer를 건너뜁니다.")
        return pd.DataFrame()

    #end_date = _utcnow() # 한국 시간 기준 당일 종가까지 사용, 서버 사용시 주석 해제
    end_date = datetime.strptime("2024-10-30", "%Y-%m-%d") #임시 고정 날짜
    start_date = end_date - timedelta(days=600)

    all_ohlcv_df: List[pd.DataFrame] = []
    for ticker in tickers:
        try:
            ohlcv_df = fetch_ohlcv(
                ticker=ticker,
                start=start_date.strftime("%Y-%m-%d"),
                end=end_date.strftime("%Y-%m-%d"),
                db_name=db_name
            )
            if ohlcv_df is None or ohlcv_df.empty:
                print(f"[WARN] OHLCV 미수집: {ticker}")
                continue
            ohlcv_df = ohlcv_df.copy()
            ohlcv_df["ticker"] = ticker
            all_ohlcv_df.append(ohlcv_df)
        except Exception as e:
            print(f"[ERROR] OHLCV 수집 실패({ticker}): {e}")

    if not all_ohlcv_df:
        print("[ERROR] 어떤 티커에서도 OHLCV 데이터를 가져오지 못했습니다.")
        return pd.DataFrame()

    raw_data = pd.concat(all_ohlcv_df, ignore_index=True)

    finder_df = pd.DataFrame(tickers, columns=["ticker"])
    transformer_result: Dict = run_transformer(
        finder_df=finder_df,
        seq_len=60,
        pred_h=1,
        raw_data=raw_data
    ) or {}

    logs_df: pd.DataFrame = transformer_result.get("logs", pd.DataFrame())
    if logs_df is None or logs_df.empty:
        print("[WARN] Transformer 결과 로그가 비어 있습니다.")
        return pd.DataFrame()

    # 필수 컬럼 검증
    missing_cols = REQUIRED_LOG_COLS - set(logs_df.columns)
    if missing_cols:
        print(f"[ERROR] 결정 로그에 필수 컬럼 누락: {sorted(missing_cols)}")
        return pd.DataFrame()

    print("--- [PIPELINE-STEP 2] Transformer 모듈 실행 완료 ---")
    return logs_df

def run_xai_report(decision_log: pd.DataFrame) -> List[str]:
    """
    결정 로그를 바탕으로 실제 XAI 리포트를 생성합니다.
    """
    print("--- [PIPELINE-STEP 3] XAI 리포트 생성 시작 ---")
    api_key = os.environ.get("GROQ_API_KEY")
    if not api_key:
        raise ValueError("XAI 리포트 생성을 위해 GROQ_API_KEY 환경 변수를 설정해주세요.")

    if decision_log is None or decision_log.empty:
        print("[WARN] 비어있는 결정 로그가 입력되어 XAI 리포트를 생성하지 않습니다.")
        return []

    reports: List[str] = []
    for _, row in decision_log.iterrows():
        try:
            decision = {
                "ticker": row["ticker"],
                "date": row["date"],
                "signal": row["action"],
                "price": float(row["price"]),
                "evidence": [
                    {"feature_name": row["feature1"], "contribution": float(row["prob1"])},
                    {"feature_name": row["feature2"], "contribution": float(row["prob2"])},
                    {"feature_name": row["feature3"], "contribution": float(row["prob3"])},
                ],
            }
            report = run_xai(decision, api_key)
            reports.append(str(report))
            print(f"--- {row['ticker']} XAI 리포트 생성 완료 ---")
        except Exception as e:
            error_message = f"--- {row.get('ticker', 'UNKNOWN')} XAI 리포트 생성 중 오류 발생: {e} ---"
            print(error_message)
            reports.append(error_message)

    print("--- [PIPELINE-STEP 3] XAI 리포트 생성 완료 ---")
    return reports



def run_pipeline() -> Optional[List[str]]:
    """
    전체 파이프라인(Finder -> Transformer -> XAI)을 실행합니다.
    """
    # 1) Finder
    tickers = run_weekly_finder()
    if not tickers:
        print("[STOP] Finder에서 종목을 찾지 못해 파이프라인을 중단합니다.")
        return None

    # 2) Transformer
    logs_df = run_signal_transformer(tickers, MARKET_DB_NAME)
    if logs_df is None or logs_df.empty:
        print("[STOP] Transformer에서 신호를 생성하지 못해 파이프라인을 중단합니다.")
        return None

    # 3) XAI
    reports = run_xai_report(logs_df)

    # 4) 저장
    save_reports_to_db(reports, REPORT_DB_NAME)

    return reports

# --- 테스트 실행 ---
if __name__ == "__main__":
    print(">>> 파이프라인 (Finder -> Transformer -> XAI) 테스트를 시작합니다.")
    final_reports = run_pipeline()
    print("\n>>> 최종 반환 결과 (XAI Reports):")
    if final_reports:
        for report in final_reports:
            print(report)
    else:
        print("생성된 리포트가 없습니다.")
    print("\n---")
    print("테스트가 정상적으로 완료되었다면, 위 '최종 반환 결과'에 각 종목에 대한 XAI 리포트가 출력되어야 합니다.")
    print("---")
