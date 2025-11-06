import os
import sys
from typing import List, Dict, Optional, Tuple
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

# === (신규 전용) 필수 컬럼: inference 로그 → XAI 변환에 필요한 것만 강제 ===
REQUIRED_LOG_COLS = {
    "ticker", "date", "action", "price",
    # XAI evidence 구성에 꼭 필요한 신규 컬럼
    "feature_name1", "feature_name2", "feature_name3",
    "feature_score1", "feature_score2", "feature_score3",
    # (원하면 로깅/모니터링용 확률도 계속 받되 필수는 아님)
}

def run_weekly_finder() -> List[str]:
    """
    주간 종목 발굴(Finder)을 실행하고 결과(종목 리스트)를 반환합니다.
    """
    print("--- [PIPELINE-STEP 1] Finder 모듈 실행 시작 ---")
    # top_tickers = run_finder()  # TODO: 종목 선정 이슈 해결 후 사용
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

    # end_date = _utcnow()  # 서버 사용 시
    end_date = datetime.strptime("2024-10-30", "%Y-%m-%d")  # 임시 고정 날짜
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

    # === 신규 포맷 강제 체크 ===
    missing_cols = REQUIRED_LOG_COLS - set(logs_df.columns)
    if missing_cols:
        print(f"[ERROR] 결정 로그에 필수 컬럼 누락(신규 포맷 전용): {sorted(missing_cols)}")
        return pd.DataFrame()

    print("--- [PIPELINE-STEP 2] Transformer 모듈 실행 완료 ---")
    return logs_df

# --- 안전 변환 유틸 ---
def _to_iso_date(v) -> str:
    try:
        if isinstance(v, (pd.Timestamp, datetime)):
            return v.strftime("%Y-%m-%d")
        return str(v)
    except Exception:
        return str(v)

def _to_float(v, fallback=0.0) -> float:
    try:
        f = float(v)
        if pd.isna(f):
            return float(fallback)
        return f
    except Exception:
        return float(fallback)

# --- XAI 리포트: 5-튜플(rows)로 반환 ---
def run_xai_report(decision_log: pd.DataFrame) -> List[Tuple[str, str, float, str, str]]:
    """
    반환: List[(ticker, signal, price, date, report_text)]
    XAI 포맷:
        {
          "ticker": "...",
          "date": "YYYY-MM-DD",
          "signal": "BUY|HOLD|SELL",
          "price": float,
          "evidence": [
            {"feature_name": str, "contribution": float},  # 0~1 점수 권장
            ...
          ]
        }
    ※ 신규 포맷 전용:
        - feature_name1~3, feature_score1~3 필수
    """
    print("--- [PIPELINE-STEP 3] XAI 리포트 생성 시작 ---")
    api_key = os.environ.get("GROQ_API_KEY")
    if not api_key:
        print("[STOP] GROQ_API_KEY 미설정: XAI 리포트 단계를 건너뜁니다.")
        return []

    if decision_log is None or decision_log.empty:
        print("[WARN] 비어있는 결정 로그가 입력되어 XAI 리포트를 생성하지 않습니다.")
        return []

    # 신규 포맷 강제(안전망)
    for c in ["feature_name1","feature_name2","feature_name3",
              "feature_score1","feature_score2","feature_score3"]:
        if c not in decision_log.columns:
            print(f"[ERROR] XAI: 신규 포맷 필수 컬럼 누락: {c}")
            return []

    rows: List[Tuple[str, str, float, str, str]] = []

    for _, row in decision_log.iterrows():
        ticker = str(row.get("ticker", "UNKNOWN"))
        date_s = _to_iso_date(row.get("date", ""))
        signal = str(row.get("action", ""))   # action -> signal
        price  = _to_float(row.get("price", 0.0))

        # === 신규 포맷 전용 evidence ===
        evidence: List[Dict[str, float]] = []
        for i in (1, 2, 3):
            name = row.get(f"feature_name{i}")
            score = row.get(f"feature_score{i}")
            # 이름/점수 모두 있어야 추가
            if name is None or str(name).strip() == "":
                continue
            if score is None or pd.isna(score):
                continue
            evidence.append({
                "feature_name": str(name),
                "contribution": _to_float(score, 0.0)  # 0~1 정규화 점수
            })

        decision_payload = {
            "ticker": ticker,
            "date": date_s,
            "signal": signal,
            "price": price,
            "evidence": evidence,
        }

        try:
            report_text = run_xai(decision_payload, api_key)
            report_text = str(report_text)  # 혹시 모를 비문자 타입 대비
            print(f"--- {ticker} XAI 리포트 생성 완료 ---")
        except Exception as e:
            report_text = f"[ERROR] XAI 리포트 생성 실패: {e}"
            print(f"--- {ticker} XAI 리포트 생성 중 오류: {e} ---")

        rows.append((ticker, signal, price, date_s, report_text))

    print("--- [PIPELINE-STEP 3] XAI 리포트 생성 완료 ---")
    return rows


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
