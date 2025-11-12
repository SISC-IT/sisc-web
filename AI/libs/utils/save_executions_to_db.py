# libs/utils/save_executions_to_db.py
# -*- coding: utf-8 -*-
"""
한국어 주석:
- 간소화 백테스터의 체결 내역(DataFrame)을 executions 테이블에 저장한다.
- 주요 컬럼:
  run_id, ticker, signal_date, signal_price, signal, fill_date, fill_price, qty, side,
  value, commission, cash_after, position_qty, avg_price, pnl_realized, pnl_unrealized, created_at
- DB 엔진: libs.utils.get_db_conn 모듈의 get_engine(db_name) 사용(프로젝트 규약 준수)
"""

from __future__ import annotations
from typing import Iterable, Optional
from sqlalchemy import text
from datetime import datetime, timezone

from libs.utils.get_db_conn import get_engine  # 프로젝트 기존 헬퍼 사용

def _utcnow_iso() -> str:
    """한국어 주석: created_at 등의 기록용 ISO8601 타임스탬프 문자열"""
    return datetime.now(timezone.utc).isoformat()

def ensure_exec_table_schema(engine) -> None:
    """
    한국어 주석:
    - executions 테이블이 없으면 생성한다.
    - 이미 있을 경우는 CREATE TABLE IF NOT EXISTS로 무해.
    - 컬럼 타입은 PostgreSQL 기준(NUMERIC 정밀도 넉넉히 설정).
      * SQLite를 쓴다면 NUMERIC이 실수로 저장되지만 문제 없이 동작.
    """
    with engine.begin() as conn:
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS executions (
            id SERIAL PRIMARY KEY,
            run_id VARCHAR(64),
            ticker VARCHAR(20) NOT NULL,
            signal_date DATE NOT NULL,
            signal_price NUMERIC(18,6),
            signal VARCHAR(10) NOT NULL,
            fill_date DATE NOT NULL,
            fill_price NUMERIC(18,6) NOT NULL,
            qty INTEGER NOT NULL,
            side VARCHAR(5) NOT NULL,
            value NUMERIC(20,6) NOT NULL,
            commission NUMERIC(18,6) NOT NULL,
            cash_after NUMERIC(20,6) NOT NULL,
            position_qty INTEGER NOT NULL,
            avg_price NUMERIC(18,6) NOT NULL,
            pnl_realized NUMERIC(18,6) NOT NULL,
            pnl_unrealized NUMERIC(18,6) NOT NULL,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
        );
        """))

def save_executions_to_db(rows_df, db_name: str) -> None:
    """
    한국어 주석:
    - 체결 내역 DataFrame(rows_df)을 executions 테이블에 일괄 insert 한다.
    - rows_df는 backtest()가 반환한 fills_df 스키마를 그대로 따른다.
    - 빈 DF가 들어오면 아무 것도 하지 않는다.
    """
    if rows_df is None or rows_df.empty:
        # 저장할 내용 없음
        return

    engine = get_engine(db_name)
    ensure_exec_table_schema(engine)

    # dict 레코드 리스트로 변환하여 executemany 형태로 성능 확보
    payload = rows_df.to_dict(orient="records")

    with engine.begin() as conn:
        sql = text("""
        INSERT INTO executions
        (run_id, ticker, signal_date, signal_price, signal, fill_date, fill_price, qty, side,
         value, commission, cash_after, position_qty, avg_price, pnl_realized, pnl_unrealized, created_at)
        VALUES
        (:run_id, :ticker, :signal_date, :signal_price, :signal, :fill_date, :fill_price, :qty, :side,
         :value, :commission, :cash_after, :position_qty, :avg_price, :pnl_realized, :pnl_unrealized, NOW())
        """)
        conn.execute(sql, payload)
