# pipeline/run_pipeline.py
# -*- coding: utf-8 -*-
"""
한국어 주석:
- 본 파일은 주간 파이프라인(종목 발굴 → 신호 변환 → 백테스트(체결/수량) → XAI 리포트 → DB 저장)을 한 번에 실행한다.
- 핵심 변화:
  1) Backtester 단계 신설: Transformer 의사결정 로그의 price를 그대로 체결가 기준으로 사용(OHLCV 불필요)
  2) 체결내역을 executions 테이블에 저장(save_executions_to_db)
  3) XAI 리포트를 기존대로 reports 테이블(또는 xai_reports)에 저장(save_reports_to_db)
- 주의:
  - decision_log에는 반드시 ['ticker','date','action','price']가 있어야 한다.
  - XAI용 필수 feature 컬럼(feature_name1~3, feature_score1~3) 점검 로직 포함.
"""

import os
import sys
from typing import List, Dict, Optional, Tuple
from datetime import datetime, timezone
import pandas as pd

# --- 프로젝트 루트 경로 설정 ---
project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(project_root)
# ------------------------------

# --- 모듈 import ---
from finder.main import run_finder                       # 1) 종목 발굴
from transformer.main import run_transformer             # 2) 신호 생성(결정 로그 생성)
from xai.run_xai import run_xai                          # 4) XAI 리포트 텍스트 생성
from libs.utils.save_reports_to_db import save_reports_to_db  # 5) 리포트 저장
from libs.utils.get_db_conn import get_db_conn           # (옵션) DB 연결 헬퍼
from backtest.simple_backtester import backtest, BacktestConfig  # 3) 백테스팅(간소화)
from libs.utils.save_executions_to_db import save_executions_to_db # 3.5) 체결내역 저장
# ---------------------------------

# DB 이름 상수(실제 등록된 키와 반드시 일치해야 함)
MARKET_DB_NAME = "db"          # (현재 파이프라인에선 직접 사용 안함, 향후 확장 대비)
REPORT_DB_NAME = "db"            # 체결내역/리포트 저장용 DB, 하나로 통합된 예정

# === (신규 전용) XAI 필수 컬럼: inference 로그 → XAI 변환에 필요한 것만 강제 ===
REQUIRED_LOG_COLS = {
    "ticker", "date", "action", "price",
    # XAI evidence 구성에 꼭 필요한 신규 컬럼
    "feature_name1", "feature_name2", "feature_name3",
    "feature_score1", "feature_score2", "feature_score3",
    # (원하면 로깅/모니터링용 확률도 계속 받되 필수는 아님)
}

# === 유틸 ===
def _utcnow() -> datetime:
    """한국어 주석: UTC now 헬퍼(테스트 재현성을 위해 따로 분리)"""
    return datetime.now(timezone.utc)

def _to_iso_date(v) -> str:
    """한국어 주석: pandas.Timestamp/ datetime → YYYY-MM-DD 문자열로 안전 변환"""
    try:
        if isinstance(v, (pd.Timestamp, datetime)):
            return v.strftime("%Y-%m-%d")
        return str(v)
    except Exception:
        return str(v)

def _to_float(v, fallback=0.0) -> float:
    """한국어 주석: 숫자 변환 시 NaN/예외에 대해 기본값 제공"""
    try:
        f = float(v)
        if pd.isna(f):
            return float(fallback)
        return f
    except Exception:
        return float(fallback)

# === 1) Finder: 주간 종목 추출 ===
def run_weekly_finder() -> List[str]:
    """
    한국어 주석:
    - Finder 모듈을 실행하여 후보 티커 리스트를 반환한다.
    - 현재는 run_finder()의 구현/데이터 이슈로 인해 임시 티커를 반환할 수 있다.
    """
    print("--- [PIPELINE-STEP 1] Finder 모듈 실행 시작 ---")
    try:
        # 실제 구현 연결 시 사용
        tickers = run_finder()
        if not tickers:
            # 비상용 임시 리스트
            tickers = ["AAPL", "MSFT", "GOOGL"]
    except Exception as e:
        print(f"[WARN] Finder 실행 중 오류: {e} → 임시 티커 사용")
        tickers = ["AAPL", "MSFT", "GOOGL"]

    print(f"--- [PIPELINE-STEP 1] Finder 완료: tickers={tickers} ---")
    return tickers

# === 2) Transformer: 신호/로그 생성 ===
def run_signal_transformer(tickers: List[str], db_name: str) -> pd.DataFrame:
    """
    한국어 주석:
    - Transformer 모듈을 실행하여 의사결정 로그(decision logs)를 반환한다.
    - 반환 데이터프레임 예시 컬럼:
      ['ticker','date','action','price','feature_name1','feature_score1', ...]
    - XAI 단계의 필수 컬럼(REQUIRED_LOG_COLS)을 사전 점검한다.
    """
    print("--- [PIPELINE-STEP 2] Transformer 모듈 실행 시작 ---")
    if not tickers:
        print("[WARN] 빈 종목 리스트가 입력되어 Transformer를 건너뜁니다.")
        return pd.DataFrame()

    # Transformer 인터페이스 규약: finder_df, seq_len, pred_h, raw_data 등 필요 시 맞춰 전달
    # (여기서는 run_transformer 내부에서 데이터 수집/전처리까지 처리한다고 가정)
    finder_df = pd.DataFrame(tickers, columns=["ticker"])

    transformer_result: Dict = run_transformer(
        finder_df=finder_df,
        seq_len=60,
        pred_h=1,
        raw_data=None  # OHLCV 비사용 파이프라인이므로 None 전달(내부에서 자체 처리할 수 있음)
    ) or {}

    logs_df: pd.DataFrame = transformer_result.get("logs", pd.DataFrame())
    if logs_df is None or logs_df.empty:
        print("[WARN] Transformer 결과 로그가 비어 있습니다.")
        return pd.DataFrame()

    # 신규 포맷 강제 체크(XAI 및 백테스터가 기대하는 컬럼 존재 여부 확인)
    missing_cols = REQUIRED_LOG_COLS - set(logs_df.columns)
    if missing_cols:
        print(f"[ERROR] 결정 로그에 필수 컬럼 누락(신규 포맷 전용): {sorted(missing_cols)}")
        return pd.DataFrame()

    print(f"--- [PIPELINE-STEP 2] Transformer 완료: logs={len(logs_df)} rows ---")
    return logs_df

# === 3) Backtester: 로그 price 기준 체결가/수량 산출 ===
def run_backtester(decision_log: pd.DataFrame) -> pd.DataFrame:
    """
    한국어 주석:
    - Transformer 의사결정 로그의 price를 체결 기준가로 사용(OHLCV 불필요)
    - 슬리피지/수수료/사이징을 BacktestConfig로 제어
    - 반환: 체결내역 DataFrame (채결가, 수량, 실현손익 등 포함)
    """
    print("--- [PIPELINE-STEP 3] Backtester 실행 시작 ---")
    if decision_log is None or decision_log.empty:
        print("[WARN] Backtester: 비어있는 결정 로그가 입력되었습니다.")
        return pd.DataFrame()

    # 동일 실행 묶음 식별자(run_id)
    run_id = _utcnow().strftime("run-%Y%m%d-%H%M%S")

    cfg = BacktestConfig(
        initial_cash=100_000.0,  # 시작 현금
        slippage_bps=5.0,        # 슬리피지 5bp
        commission_bps=3.0,      # 수수료 3bp
        risk_frac=0.2,           # 1회 진입에 현금의 20% 사용
        max_positions_per_ticker=1,
        fill_on_same_day=True    # 로그 가격으로 '즉시' 체결
    )

    fills_df, summary = backtest(
        decision_log=decision_log,
        config=cfg,
        run_id=run_id
    )

    if fills_df is None or fills_df.empty:
        print("[WARN] Backtester: 생성된 체결 내역이 없습니다.")
        return pd.DataFrame()

    print(f"--- [PIPELINE-STEP 3] 완료: trades={len(fills_df)}, "
          f"cash_final={summary.get('cash_final')}, pnl_realized_sum={summary.get('pnl_realized_sum')} ---")
    return fills_df

# === 4) XAI 리포트: 5-튜플(rows) 생성 ===
def run_xai_report(decision_log: pd.DataFrame) -> List[Tuple[str, str, float, str, str]]:
    """
    한국어 주석:
    - 입력: Transformer 결정 로그
    - 출력: List[(ticker, signal, price, date, report_text)]
    - GROQ_API_KEY 미설정 시 XAI 단계를 건너뛰고 빈 리스트 반환
    - 필수 feature 컬럼 존재 검사 포함
    """
    print("--- [PIPELINE-STEP 4] XAI 리포트 생성 시작 ---")
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

        # === 신규 포맷 전용 evidence(간단 직렬화) ===
        evidence: List[Dict[str, float]] = []
        for i in (1, 2, 3):
            name = row.get(f"feature_name{i}")
            score = row.get(f"feature_score{i}")
            if name is None or str(name).strip() == "":
                continue
            if score is None or pd.isna(score):
                continue
            evidence.append({"feature_name": str(name), "contribution": _to_float(score, 0.0)})

        decision_payload = {
            "ticker": ticker,
            "date": date_s,
            "signal": signal,
            "price": price,
            "evidence": evidence,
        }

        try:
            report_text = run_xai(decision_payload, api_key)
            report_text = str(report_text)
            print(f"--- {ticker} XAI 리포트 생성 완료 ---")
        except Exception as e:
            report_text = f"[ERROR] XAI 리포트 생성 실패: {e}"
            print(f"--- {ticker} XAI 리포트 생성 중 오류: {e} ---")

        rows.append((ticker, signal, price, date_s, report_text))

    print("--- [PIPELINE-STEP 4] XAI 리포트 생성 완료 ---")
    return rows

# === 파이프라인 오케스트레이션 ===
def run_pipeline() -> Optional[List[Tuple[str, str, float, str, str]]]:
    """
    한국어 주석:
    - 전체 파이프라인(Finder -> Transformer -> Backtester -> XAI -> 저장)을 실행한다.
    - 반환: XAI 리포트 rows(5-튜플 리스트), 저장은 내부에서 수행.
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

    # 3) Backtester (의사결정 로그 price로 체결)
    fills_df = run_backtester(logs_df)

    # 3.5) 체결 내역 DB 저장(선택 사항이지만 기본 저장 권장)
    try:
        save_executions_to_db(fills_df, REPORT_DB_NAME)
        print("[INFO] 체결 내역을 DB에 저장했습니다.")
    except Exception as e:
        print(f"[WARN] 체결 내역 DB 저장 실패: {e}")

    # 4) XAI
    reports = run_xai_report(logs_df)

    # 5) 리포트 저장
    try:
        save_reports_to_db(reports, REPORT_DB_NAME)
        print("[INFO] XAI 리포트를 DB에 저장했습니다.")
    except Exception as e:
        print(f"[WARN] XAI 리포트 DB 저장 실패: {e}")

    return reports

# --- 테스트 실행 ---
if __name__ == "__main__":
    print(">>> 파이프라인 (Finder -> Transformer -> Backtester -> XAI) 테스트를 시작합니다.")
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
