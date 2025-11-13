# AI/tests/test_transformer_live_fetch.py
# -*- coding: utf-8 -*-
"""
[목적]
- Transformer 모듈만 실제 OHLCV로 테스트 (저장 없음, 출력만)
- 데이터 수집은 프로젝트 표준 유틸: libs.utils.fetch_ohlcv.fetch_ohlcv 를 '그대로' 사용
  (즉, DB 우선 조회 + 실패/결측 시 야후 파이낸스 API 폴백 로직은 fetch_ohlcv 내부 정책을 따름)

[실행]
> cd AI
> python -m tests.test_transformer_live_fetch
또는
> python tests/test_transformer_live_fetch.py
"""

import os
import sys
import json
from typing import Dict, List, Optional
from datetime import datetime, timedelta
import time
import random

import pandas as pd
import numpy as np

# --- 프로젝트 루트 경로 설정 ---------------------------------------------------
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(project_root)
# ---------------------------------------------------------------------------

# --- 모듈 임포트 --------------------------------------------------------------
from transformer.main import run_transformer               # Transformer 단독 테스트 대상
from libs.utils.fetch_ohlcv import fetch_ohlcv            # ★ 표준 OHLCV 수집 유틸(반드시 이걸 사용)
# ---------------------------------------------------------------------------


# =============================================================================
# (옵션) 안전한 fetch 래퍼: 일시적 실패/429 등에 대비한 재시도
#   - fetch_ohlcv 내부에서도 재시도/폴백이 구현되어 있을 수 있으나,
#     테스트 안정성을 위해 여기서 추가로 얇은 재시도를 감쌉니다.
# =============================================================================
def safe_fetch_ohlcv(
    ticker: str,
    start: str,
    end: str,
    config: Optional[Dict] = None,
    max_retries: int = 5,
    base_sleep: float = 0.8
) -> pd.DataFrame:
    """
    fetch_ohlcv 호출을 얇게 감싸는 재시도 래퍼.
    - 429/일시 네트워크 오류 같은 경우를 대비하여 지수 백오프 + 지터 적용
    - fetch_ohlcv가 raise하면 여기서 재시도 후 최종 raise
    """
    attempt = 0
    while True:
        try:
            df = fetch_ohlcv(
                ticker=ticker,
                start=start,
                end=end,
                config=(config or {})
            )
            return df
        except Exception as e:
            attempt += 1
            if attempt >= max_retries:
                raise
            # 지수 백오프 + 약간의 랜덤 지터
            sleep_s = base_sleep * (2 ** (attempt - 1)) + random.uniform(0, 0.6)
            print(f"[WARN] {ticker} fetch_ohlcv 실패({attempt}/{max_retries}) -> {e} | {sleep_s:.2f}s 대기 후 재시도")
            time.sleep(sleep_s)


# =============================================================================
# Transformer 단독 라이브 테스트
# =============================================================================
def run_transform_only_live_with_fetch():
    """
    - configs/config.json 로드 → db 설정 전달
    - 티커별로 fetch_ohlcv 호출(★ 프로젝트 유틸 사용) → raw_data 결합
    - run_transformer 호출 → 출력만 수행(저장 없음)
    """
    print("=== [TEST] Transformer 단독(실데이터) 테스트 시작 — using libs.utils.fetch_ohlcv ===")

    # ----------------------------------------------------------------------
    # (A) 설정/입력
    # ----------------------------------------------------------------------
    cfg_path = os.path.join(project_root, "configs", "config.json")
    config: Dict = {}
    if os.path.isfile(cfg_path):
        try:
            with open(cfg_path, "r", encoding="utf-8") as f:
                config = json.load(f)
            print("[INFO] configs/config.json 로드 완료")
        except Exception as e:
            print(f"[WARN] config 로드 실패(빈 설정으로 진행): {e}")

    db_config = (config or {}).get("db", {})  # fetch_ohlcv에 그대로 넘김

    # 테스트 티커/기간
    tickers: List[str] = ["AAPL", "MSFT", "GOOGL"]  # 필요 시 교체
    end_dt = datetime.now()
    start_dt = end_dt - timedelta(days=600)
    start_str = start_dt.strftime("%Y-%m-%d")
    end_str = end_dt.strftime("%Y-%m-%d")

    seq_len = 60
    pred_h = 1
    transformer_cfg: Dict = {
        "transformer": {
            "features": ["open", "high", "low", "close", "volume","adjusted_close"],
            "target": "close",
            "scaler": "standard"
        }
    }

    # ----------------------------------------------------------------------
    # (B) fetch_ohlcv 로 실데이터 가져오기 (티커별 → concat)
    # ----------------------------------------------------------------------
    raw_parts: List[pd.DataFrame] = []
    for tkr in tickers:
        try:
            print(f"[INFO] 수집 시작: {tkr} ({start_str} → {end_str})")
            df = safe_fetch_ohlcv(
                ticker=tkr,
                start=start_str,
                end=end_str,
                config=db_config,         # ★ fetch_ohlcv는 내부에서 DB/야후 폴백 처리
                max_retries=5,
                base_sleep=0.8
            )
            if df is None or df.empty:
                print(f"[WARN] {tkr} 데이터가 비어 있습니다.")
            else:
                # 스키마 정합성(Transformer가 기대하는 컬럼 존재 검사)
                required = ["ticker", "date", "open", "high", "low", "close", "volume", "adjusted_close"]
                missing = [c for c in required if c not in df.columns]
                if missing:
                    raise ValueError(f"{tkr} 수집 데이터에 필수 컬럼 누락: {missing}")
                # 날짜형 변환 보정
                if not np.issubdtype(df["date"].dtype, np.datetime64):
                    df["date"] = pd.to_datetime(df["date"], errors="coerce")
                raw_parts.append(df.reset_index(drop=True))
                print(f"[INFO] {tkr} 수집 완료: {len(df):,} rows")
        finally:
            # API rate 제한 완화(티커 사이 간격)
            time.sleep(0.6 + random.uniform(0, 0.6))

    if not raw_parts:
        print("[ERROR] 모든 소스에서 OHLCV 확보 실패(fetch_ohlcv 사용).")
        return

    raw_data = pd.concat(raw_parts, ignore_index=True)

    # ----------------------------------------------------------------------
    # (C) Transformer 호출
    # ----------------------------------------------------------------------
    finder_df = pd.DataFrame({"ticker": tickers})

    print("[INFO] run_transformer 호출 중...")
    result = run_transformer(
        finder_df=finder_df,
        seq_len=seq_len,
        pred_h=pred_h,
        raw_data=raw_data,
        config=transformer_cfg
    )

    logs_df = result.get("logs", pd.DataFrame()) if isinstance(result, dict) else pd.DataFrame()
    meta = {k: v for k, v in result.items() if k != "logs"} if isinstance(result, dict) else {}

    # ----------------------------------------------------------------------
    # (D) 출력만(저장 없음)
    # ----------------------------------------------------------------------
    print("\n--- [RESULT] Transformer 반환 메타 키 ---")
    print(list(meta.keys()))

    print("\n--- [RESULT] 결정 로그(logs) 미리보기 ---")
    if not logs_df.empty:
        if not np.issubdtype(logs_df["date"].dtype, np.datetime64):
            logs_df["date"] = pd.to_datetime(logs_df["date"], errors="coerce")
        print(logs_df.head(10).to_string(index=False))
    else:
        print("logs_df가 비어 있습니다. Transformer 내부 로직을 확인하세요.")

    if not logs_df.empty:
        if "action" in logs_df.columns:
            print("\n--- [STATS] 액션별 건수 ---")
            print(logs_df["action"].value_counts())
        if {"ticker", "date"}.issubset(logs_df.columns):
            print("\n--- [STATS] 티커별 최근 신호 2건 ---")
            latest = (
                logs_df.sort_values(["ticker", "date"], ascending=[True, False])
                .groupby("ticker")
                .head(2)
                .reset_index(drop=True)
            )
            print(latest.to_string(index=False))

    print(f"\n=== [TEST] 종료: 총 원시행(raw_data) = {len(raw_data):,} ===")


# -----------------------------------------------------------------------------
# 엔트리포인트
# -----------------------------------------------------------------------------
if __name__ == "__main__":
    run_transform_only_live_with_fetch()
