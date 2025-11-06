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

# 필수 컬럼 검증
REQUIRED_NEW = {
    "ticker", "date", "action", "price",
    "feature_name1", "feature_name2", "feature_name3",
    "feature_score1", "feature_score2", "feature_score3",
    "prob1", "prob2", "prob3"
}

def _has_required_cols(df: pd.DataFrame) -> bool:
    cols = set(df.columns)
    return (REQUIRED_NEW <= cols) or (REQUIRED_OLD <= cols)

def _which_format(df: pd.DataFrame) -> str:
    cols = set(df.columns)
    if REQUIRED_NEW <= cols:
        return "new"   # feature_name*/feature_score* 사용
    if REQUIRED_OLD <= cols:
        return "old"   # feature1~3만 존재
    return "none"

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

    # 필수 컬럼 검증 (신규/구버전 모두 허용)
    if not _has_required_cols(logs_df):
        print(f"[ERROR] 결정 로그에 필수 컬럼 누락. 제공 컬럼: {sorted(set(logs_df.columns))}")
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
    """
    print("--- [PIPELINE-STEP 3] XAI 리포트 생성 시작 ---")
    api_key = os.environ.get("GROQ_API_KEY")
    if not api_key:
        print("[STOP] GROQ_API_KEY 미설정: XAI 리포트 단계를 건너뜁니다.")
        return []

    if decision_log is None or decision_log.empty:
        print("[WARN] 비어있는 결정 로그가 입력되어 XAI 리포트를 생성하지 않습니다.")
        return []

    fmt = _which_format(decision_log)
    if fmt == "none":
        print("[ERROR] 지원되지 않는 로그 포맷입니다.")
        return []

    rows: List[Tuple[str, str, float, str, str]] = []

    for _, row in decision_log.iterrows():
        ticker = str(row.get("ticker", "UNKNOWN"))
        date_s = _to_iso_date(row.get("date", ""))
        signal = str(row.get("action", ""))   # action -> signal
        price  = _to_float(row.get("price", 0.0))

        # evidence 구성
        evidence: List[Dict[str, Optional[float]]] = []
        if fmt == "new":
            # 권장: 이름 + 정규화 점수(0~1)
            for i in (1, 2, 3):
                name = row.get(f"feature_name{i}")
                score = row.get(f"feature_score{i}")
                if name is None or str(name).strip() == "":
                    continue
                contrib = None if pd.isna(score) else _to_float(score, None)
                evidence.append({"feature_name": str(name), "contribution": contrib})
        else:
            # 구버전: 이름 컬럼이 없음 -> 값 자체를 이름처럼 쓰고 랭크 가중치로 대체(임시)
            rank_contrib = [1.0, 2/3, 1/3]
            for i, rc in zip((1, 2, 3), rank_contrib):
                val = row.get(f"feature{i}")
                if pd.isna(val):
                    continue
                evidence.append({"feature_name": str(val), "contribution": rc})

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
