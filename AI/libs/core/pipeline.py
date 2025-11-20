# pipeline/run_pipeline.py 
# -*- coding: utf-8 -*-
"""
한국어 주석 (개요):
- 본 파일은 "주간 자동 파이프라인"의 전체 흐름을 오케스트레이션한다.
  (Finder → Transformer → XAI 리포트 → Backtester → DB 저장)

[전체 플로우]
1) Finder
   - 시장/전략 조건에 맞는 종목 목록(ticker list)을 선정한다.

2) Transformer
   - 선택된 종목들의 OHLCV를 DB에서 가져온다(fetch_ohlcv).
   - LSTM/Rule 기반 등의 Transformer 로직을 통해 의사결정 로그(DataFrame)를 생성한다.
   - 이 의사결정 로그는 XAI와 Backtester에서 모두 공통으로 사용된다.

3) XAI (e.g. GROQ 등 LLM 기반 설명 생성)
   - 각 의사결정에 대해 feature_name / feature_score를 기반으로
     "왜 이 신호가 나왔는지"에 대한 자연어 리포트를 생성한다.
   - 결과는 xai_reports 테이블에 먼저 저장된다.
   - 이 때 생성된 xai_reports.id를 decision_log(logs_df)에 xai_report_id로 심는다.

4) Backtester
   - xai_report_id가 포함된 의사결정 로그(decision_log)를 받아,
     price 컬럼을 "체결 기준가"로 직접 사용해 간소화된 백테스트를 수행한다.
   - Backtest 결과(fills_df)는 xai_report_id를 그대로 보존한 상태로 executions에 저장된다.

[주의 사항]
- Transformer가 생성하는 decision_log(DataFrame)는 최소한 아래 컬럼을 포함해야 한다.
  ['ticker', 'date', 'action', 'price',
   'feature_name1', 'feature_name2', 'feature_name3',
   'feature_score1', 'feature_score2', 'feature_score3']
- GROQ_API_KEY 환경변수가 없으면 XAI 단계는 자동으로 스킵된다.
"""

import os
import sys
from typing import List, Dict, Optional, Tuple
from datetime import datetime, timezone, timedelta

import pandas as pd

# ----------------------------------------------------------------------
# 프로젝트 루트 경로 설정
# ----------------------------------------------------------------------
project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(project_root)

# ----------------------------------------------------------------------
# 외부 모듈 import (각 단계별 역할)
# ----------------------------------------------------------------------
from finder.main import run_finder                                # 1) 종목 발굴
from transformer.main import run_transformer                      # 2) 신호 생성(의사결정 로그 생성)
from backtest.simple_backtester import backtest, BacktestConfig   # 4) 백테스팅(간소화 체결 엔진)
from libs.utils.save_executions_to_db import save_executions_to_db  # 5) 체결내역 DB 저장
from xai.run_xai import run_xai                                   # 3) XAI 리포트 텍스트 생성
from libs.utils.save_reports_to_db import save_reports_to_db      # 3.5) XAI 리포트 DB 저장 (id 반환)
from libs.utils.fetch_ohlcv import fetch_ohlcv                    # (Transformer용) OHLCV 수집 헬퍼

# ----------------------------------------------------------------------
# 타입 별칭 (다른 모듈과의 데이터 계약을 명확히 하기 위함)
# ----------------------------------------------------------------------
ReportRow = Tuple[str, str, float, str, str]  # (ticker, signal, price, date, report_text)

# ----------------------------------------------------------------------
# DB 이름 상수
# ----------------------------------------------------------------------
MARKET_DB_NAME = "db"   # 시세/시장 데이터(DB) 명.
REPORT_DB_NAME = "db"   # 체결내역 / XAI 리포트를 저장하는 DB 명

# ----------------------------------------------------------------------
# XAI 및 Backtester에서 공통으로 요구하는 "결정 로그 필수 컬럼" 정의
# ----------------------------------------------------------------------
REQUIRED_LOG_COLS = {
    "ticker",
    "date",
    "action",
    "price",
    # XAI evidence 구성에 꼭 필요한 신규 컬럼
    "feature_name1",
    "feature_name2",
    "feature_name3",
    "feature_score1",
    "feature_score2",
    "feature_score3",
}


# ======================================================================
# 유틸리티 함수 모음
# ======================================================================

def _utcnow() -> datetime:
    """현재 시각을 UTC 기준 datetime으로 반환."""
    return datetime.now(timezone.utc)


def _to_iso_date(v) -> str:
    """값을 'YYYY-MM-DD' 문자열로 변환."""
    try:
        if isinstance(v, (pd.Timestamp, datetime)):
            return v.strftime("%Y-%m-%d")
        return str(v)
    except Exception:
        return str(v)


def _to_float(v, fallback: float = 0.0) -> float:
    """값을 float로 변환, 실패 시 fallback."""
    try:
        f = float(v)
        if pd.isna(f):
            return float(fallback)
        return f
    except Exception:
        return float(fallback)


# ======================================================================
# 1) Finder: 주간 종목 추출 단계
# ======================================================================

def run_weekly_finder() -> List[str]:
    """
    Finder 모듈을 실행하여 후보 티커 리스트를 반환.
    """
    print("--- [PIPELINE-STEP 1] Finder 모듈 실행 시작 ---")
    try:
        tickers = run_finder()
        if not tickers:
            tickers = ["AAPL", "MSFT", "GOOGL"]
    except Exception as e:
        print(f"[WARN] Finder 실행 중 오류 발생: {e} → 임시 티커 리스트를 사용합니다.")
        tickers = ["AAPL", "MSFT", "GOOGL"]

    print(f"--- [PIPELINE-STEP 1] Finder 완료: tickers={tickers} ---")
    return tickers


# ======================================================================
# 2) Transformer: 신호/의사결정 로그 생성 단계
# ======================================================================

def run_signal_transformer(tickers: List[str], db_name: str) -> pd.DataFrame:
    """
    종목 리스트에 대해 DB에서 OHLCV를 수집하고 Transformer를 호출하여
    의사결정 로그(DataFrame)를 생성한다.
    """
    print("--- [PIPELINE-STEP 2] Transformer 모듈 실행 시작 ---")

    if not tickers:
        print("[WARN] 빈 종목 리스트가 입력되어 Transformer 단계를 건너뜁니다.")
        return pd.DataFrame()

    end_date = datetime.strptime("2024-11-1", "%Y-%m-%d")  # 임시 고정 날짜
    start_date = end_date - timedelta(days=600)

    all_ohlcv_df: List[pd.DataFrame] = []

    for ticker in tickers:
        try:
            ohlcv_df = fetch_ohlcv(
                ticker=ticker,
                start=start_date.strftime("%Y-%m-%d"),
                end=end_date.strftime("%Y-%m-%d"),
                db_name=db_name,
            )

            if ohlcv_df is None or ohlcv_df.empty:
                print(f"[WARN] OHLCV 미수집: {ticker}")
                continue

            ohlcv_df = ohlcv_df.copy()

            if not pd.api.types.is_datetime64_any_dtype(ohlcv_df["date"]):
                ohlcv_df["date"] = pd.to_datetime(ohlcv_df["date"])

            ohlcv_df["ticker"] = ticker
            all_ohlcv_df.append(ohlcv_df)

        except Exception as e:
            print(f"[ERROR] OHLCV 수집 실패 (ticker={ticker}): {e}")

    if not all_ohlcv_df:
        print("[ERROR] 어떤 티커에서도 OHLCV 데이터를 가져오지 못했습니다. Transformer를 종료합니다.")
        return pd.DataFrame()

    raw_data = pd.concat(all_ohlcv_df, ignore_index=True)
    raw_data = raw_data.sort_values(["ticker", "date"]).reset_index(drop=True)

    finder_df = pd.DataFrame(tickers, columns=["ticker"])

    transformer_result: Dict = run_transformer(
        finder_df=finder_df,
        seq_len=60,
        pred_h=1,
        raw_data=raw_data,
    ) or {}

    logs_df: pd.DataFrame = transformer_result.get("logs", pd.DataFrame())

    if logs_df is None or logs_df.empty:
        print("[WARN] Transformer 결과 로그(logs)가 비어 있습니다.")
        return pd.DataFrame()

    missing_cols = REQUIRED_LOG_COLS - set(logs_df.columns)
    if missing_cols:
        print(f"[ERROR] 결정 로그에 필수 컬럼 누락 (신규 포맷 전용): {sorted(missing_cols)}")
        return pd.DataFrame()

    print("--- [PIPELINE-STEP 2] Transformer 모듈 실행 완료 ---")
    return logs_df


# ======================================================================
# 3) Backtester: 의사결정 로그 기반 체결/포지션 계산 단계
# ======================================================================

def run_backtester(decision_log: pd.DataFrame) -> pd.DataFrame:
    """
    Transformer에서 생성된 의사결정 로그(decision_log)의 price 컬럼을
    OHLCV 없이 "체결 기준가"로 직접 사용해 간소화된 백테스트를 수행한다.

    주의:
    - decision_log는 xai_report_id 컬럼을 포함할 수 있으며,
      backtest() 구현이 해당 컬럼을 드롭하지 않으면 fills_df에도 그대로 보존된다.
    """
    print("--- [PIPELINE-STEP 4] Backtester 실행 시작 ---")

    if decision_log is None or decision_log.empty:
        print("[WARN] Backtester: 비어있는 결정 로그가 입력되었습니다. 체결을 수행하지 않습니다.")
        return pd.DataFrame()

    run_id = _utcnow().strftime("run-%Y%m%d-%H%M%S")

    cfg = BacktestConfig(
        initial_cash=100_000.0,
        slippage_bps=5.0,
        commission_bps=3.0,
        risk_frac=0.2,
        max_positions_per_ticker=1,
        fill_on_same_day=True,
    )

    fills_df, summary = backtest(
        decision_log=decision_log,
        config=cfg,
        run_id=run_id,
    )

    if fills_df is None or fills_df.empty:
        print("[WARN] Backtester: 생성된 체결 내역이 없습니다.")
        return pd.DataFrame()

    print(
        f"--- [PIPELINE-STEP 4] Backtester 완료: "
        f"trades={len(fills_df)}, "
        f"cash_final={summary.get('cash_final')}, "
        f"pnl_realized_sum={summary.get('pnl_realized_sum')} ---"
    )
    return fills_df


# ======================================================================
# 4) XAI 리포트: 설명 텍스트 생성 단계
# ======================================================================

def run_xai_report(decision_log: pd.DataFrame) -> List[ReportRow]:
    """
    Transformer 결정 로그를 입력으로 받아, 각 행(의사결정)에 대한
    XAI 설명 리포트(자연어 텍스트)를 생성한다.
    """
    print("--- [PIPELINE-STEP 3] XAI 리포트 생성 시작 ---")

    api_key = os.environ.get("GROQ_API_KEY")
    if not api_key:
        print("[STOP] GROQ_API_KEY 환경변수가 설정되어 있지 않아 XAI 리포트를 생성하지 않습니다.")
        return []

    if decision_log is None or decision_log.empty:
        print("[WARN] 비어있는 결정 로그가 입력되어 XAI 리포트를 생성하지 않습니다.")
        return []

    for c in [
        "feature_name1",
        "feature_name2",
        "feature_name3",
        "feature_score1",
        "feature_score2",
        "feature_score3",
    ]:
        if c not in decision_log.columns:
            print(f"[ERROR] XAI: 신규 포맷 필수 컬럼 누락: {c}")
            return []

    rows: List[ReportRow] = []

    for _, row in decision_log.iterrows():
        ticker = str(row.get("ticker", "UNKNOWN"))
        date_s = _to_iso_date(row.get("date", ""))
        signal = str(row.get("action", ""))   # action → signal
        price = _to_float(row.get("price", 0.0))

        evidence: List[Dict[str, float]] = []
        for i in (1, 2, 3):
            name = row.get(f"feature_name{i}")
            score = row.get(f"feature_score{i}")

            if name is None or str(name).strip() == "":
                continue
            if score is None or pd.isna(score):
                continue

            evidence.append(
                {
                    "feature_name": str(name),
                    "contribution": _to_float(score, 0.0),
                }
            )

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
            print(f"--- [XAI] {ticker} 리포트 생성 완료 ---")
        except Exception as e:
            report_text = f"[ERROR] XAI 리포트 생성 실패: {e}"
            print(f"--- [XAI] {ticker} 리포트 생성 중 오류: {e} ---")

        rows.append((ticker, signal, price, date_s, report_text))

    print("--- [PIPELINE-STEP 3] XAI 리포트 생성 완료 ---")
    return rows


# ======================================================================
# 5) 전체 파이프라인 오케스트레이션
# ======================================================================

def run_pipeline() -> Optional[List[ReportRow]]:
    """
    전체 파이프라인(Finder → Transformer → XAI → Backtester → DB 저장)을
    한 번에 실행하는 엔트리 포인트 함수.
    """
    # 1) Finder
    tickers = run_weekly_finder()
    if not tickers:
        print("[STOP] Finder에서 종목을 찾지 못해 파이프라인을 중단합니다.")
        return None

    # 2) Transformer
    logs_df = run_signal_transformer(tickers, MARKET_DB_NAME)
    if logs_df is None or logs_df.empty:
        print("[STOP] Transformer에서 유효한 신호를 생성하지 못해 파이프라인을 중단합니다.")
        return None

    # 3) XAI 리포트 생성
    reports = run_xai_report(logs_df)

    # 3.5) XAI 리포트 DB 저장 → 생성된 id 리스트 수신
    try:
        xai_ids = save_reports_to_db(reports, REPORT_DB_NAME)
        print("[INFO] XAI 리포트를 DB에 저장했습니다.")
    except Exception as e:
        print(f"[WARN] XAI 리포트 DB 저장 실패: {e}")
        xai_ids = []

    # 3.7) logs_df에 xai_report_id 심기
    #      (길이가 맞지 않거나 XAI 저장 실패 시에는 NULL로 채워서 진행)
    logs_df = logs_df.copy().reset_index(drop=True)
    if xai_ids and len(xai_ids) == len(logs_df):
        logs_df["xai_report_id"] = xai_ids
    else:
        logs_df["xai_report_id"] = None
        if xai_ids and len(xai_ids) != len(logs_df):
            print(
                f"[WARN] XAI ID 개수({len(xai_ids)})와 decision_log 행 수({len(logs_df)})가 달라 "
                "xai_report_id를 매핑하지 못했습니다. (모두 NULL 처리)"
            )

    # 4) Backtester: xai_report_id 포함 decision_log로 체결 내역 생성
    fills_df = run_backtester(logs_df)

    # 5) executions 테이블에 체결 내역 저장
    try:
        save_executions_to_db(fills_df, REPORT_DB_NAME)
        print("[INFO] 체결 내역을 DB에 저장했습니다.")
    except Exception as e:
        print(f"[WARN] 체결 내역 DB 저장 실패: {e}")

    return reports


# ======================================================================
# 스크립트 단독 실행 시 테스트용 엔트리 포인트
# ======================================================================
if __name__ == "__main__":
    print(">>> 파이프라인 (Finder → Transformer → XAI → Backtester) 테스트를 시작합니다.")
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
