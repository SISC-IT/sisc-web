# -*- coding: utf-8 -*-
"""
한국어 주석:
- executions 테이블 저장 → portfolio_positions 갱신 → portfolio_summary 갱신
- 하나의 executions INSERT가 발생하면 계좌 전체 상태를 즉시 업데이트한다.
"""

from __future__ import annotations
from typing import Optional
from sqlalchemy import text
from datetime import datetime, timezone
import pandas as pd

from libs.utils.get_db_conn import get_engine  # 기존 프로젝트 헬퍼 사용


# -------------------------------------------------------------------
# 공용 헬퍼
# -------------------------------------------------------------------
def _utcnow_iso() -> str:
    """ISO 포맷 UTC 타임스탬프 문자열"""
    return datetime.now(timezone.utc).isoformat()

# -------------------------------------------------------------------
# 📌 portfolio_positions 업데이트
# -------------------------------------------------------------------
def update_portfolio_position(conn, execution: dict):
    """
    한국어 주석:
    - executions에 새 체결 1건이 기록될 때 호출
    - portfolio_positions는 ticker당 1행만 유지 (UNIQUE)
    """

    ticker = execution["ticker"]
    qty = int(execution["qty"])
    fill_price = float(execution["fill_price"])
    side = execution["side"].upper()
    realized_pnl = float(execution["pnl_realized"])

    # --- 기존 포지션 로드
    old = conn.execute(text("""
        SELECT position_qty, avg_price, pnl_realized_cum
        FROM portfolio_positions
        WHERE ticker = :ticker
    """), {"ticker": ticker}).fetchone()

    if old is None:
        # 신규 포지션 (BUY만 가능)
        if side != "BUY":
            raise ValueError(f"[ERROR] 보유량 없이 SELL 발생: {ticker}")

        new_qty = qty
        new_avg_price = fill_price
        new_realized_cum = 0.0

    else:
        old_qty = old.position_qty
        old_avg_price = float(old.avg_price)
        old_realized_cum = float(old.pnl_realized_cum)

        if side == "BUY":
            new_qty = old_qty + qty
            new_avg_price = (old_avg_price * old_qty + fill_price * qty) / new_qty
            new_realized_cum = old_realized_cum

        elif side == "SELL":
            new_qty = old_qty - qty
            new_realized_cum = old_realized_cum + realized_pnl

            if new_qty == 0:
                new_avg_price = 0.0
            else:
                new_avg_price = old_avg_price

    # 평가 관련 데이터
    current_price = fill_price
    market_value = new_qty * current_price
    pnl_unrealized = (current_price - new_avg_price) * new_qty

    # UPSERT
    conn.execute(text("""
        INSERT INTO portfolio_positions
        (ticker, position_qty, avg_price,
         current_price, market_value, pnl_unrealized,
         pnl_realized_cum, updated_at)
        VALUES
        (:ticker, :q, :avg,
         :cp, :mv, :pnl_u,
         :pnl_r, NOW())
        ON CONFLICT (ticker)
        DO UPDATE SET
            position_qty = EXCLUDED.position_qty,
            avg_price = EXCLUDED.avg_price,
            current_price = EXCLUDED.current_price,
            market_value = EXCLUDED.market_value,
            pnl_unrealized = EXCLUDED.pnl_unrealized,
            pnl_realized_cum = EXCLUDED.pnl_realized_cum,
            updated_at = NOW();
    """), {
        "ticker": ticker,
        "q": new_qty,
        "avg": new_avg_price,
        "cp": current_price,
        "mv": market_value,
        "pnl_u": pnl_unrealized,
        "pnl_r": new_realized_cum,
    })


# -------------------------------------------------------------------
# 📌 portfolio_summary 업데이트
# -------------------------------------------------------------------
def update_portfolio_summary(conn, fill_date: str):
    """
    한국어 주석:
    - 계좌 전체 요약(자산, 평가금액, 수익률)을 fill_date 기준으로 M2M 업데이트.
    - executions → portfolio_positions → portfolio_summary 순으로 호출됨.
    """

    # 1) 최신 현금(cash): executions.cash_after 기준
    cash_row = conn.execute(text("""
        SELECT cash_after
        FROM executions
        ORDER BY id DESC
        LIMIT 1;
    """)).fetchone()

    cash = float(cash_row.cash_after) if cash_row else 0.0

    # 2) 전체 평가금액 = portfolio_positions.market_value 합
    mv_row = conn.execute(text("""
        SELECT COALESCE(SUM(market_value), 0) AS mv
        FROM portfolio_positions;
    """)).fetchone()

    market_value = float(mv_row.mv)

    total_asset = cash + market_value

    # 3) 누적 실현손익
    realized_row = conn.execute(text("""
        SELECT COALESCE(SUM(pnl_realized_cum), 0) AS pnl_r
        FROM portfolio_positions;
    """)).fetchone()

    pnl_realized_cum = float(realized_row.pnl_r)

    # 4) 미실현손익 합계
    unrealized_row = conn.execute(text("""
        SELECT COALESCE(SUM(pnl_unrealized), 0) AS pnl_u
        FROM portfolio_positions;
    """)).fetchone()

    pnl_unrealized = float(unrealized_row.pnl_u)

    # 5) 초기 원금 불러오기 (없으면 total_asset으로 설정)
    init = conn.execute(text("""
        SELECT initial_capital
        FROM portfolio_summary
        ORDER BY date ASC
        LIMIT 1;
    """)).fetchone()

    if init is None:
        initial_capital = total_asset
    else:
        initial_capital = float(init.initial_capital)

    return_rate = (total_asset / initial_capital) - 1

    # UPSERT
    conn.execute(text("""
        INSERT INTO portfolio_summary
        (date, total_asset, cash, market_value,
         pnl_unrealized, pnl_realized_cum,
         initial_capital, return_rate, created_at)
        VALUES
        (:d, :ta, :cash, :mv,
         :pnl_u, :pnl_r,
         :init, :rr, NOW())
        ON CONFLICT (date)
        DO UPDATE SET
            total_asset = EXCLUDED.total_asset,
            cash = EXCLUDED.cash,
            market_value = EXCLUDED.market_value,
            pnl_unrealized = EXCLUDED.pnl_unrealized,
            pnl_realized_cum = EXCLUDED.pnl_realized_cum,
            return_rate = EXCLUDED.return_rate,
            created_at = NOW();
    """), {
        "d": fill_date,
        "ta": total_asset,
        "cash": cash,
        "mv": market_value,
        "pnl_u": pnl_unrealized,
        "pnl_r": pnl_realized_cum,
        "init": initial_capital,
        "rr": return_rate,
    })


# -------------------------------------------------------------------
# 📌 메인 함수: executions + positions + summary
# -------------------------------------------------------------------
def save_executions_to_db(rows_df: pd.DataFrame, db_name: str) -> None:
    """
    한국어 주석:
    - rows_df 전체를 executions 테이블에 저장
    - 각 행 마다 portfolio_positions 갱신
    - 마지막으로 portfolio_summary 갱신
    """

    if rows_df is None or rows_df.empty:
        return

    engine = get_engine(db_name)

    # xai_report_id 없으면 NULL로
    if "xai_report_id" not in rows_df.columns:
        rows_df = rows_df.copy()
        print("[WARN] xai_report_id 없음. NULL 처리.")
        rows_df["xai_report_id"] = None

    payload = rows_df.to_dict(orient="records")

    with engine.begin() as conn:

        # =============================================================
        # 1) executions 테이블 INSERT (배치)
        # =============================================================
        insert_sql = text("""
        INSERT INTO executions
        (run_id, xai_report_id,
         ticker, signal_date, signal_price, signal,
         fill_date, fill_price, qty, side,
         value, commission, cash_after,
         position_qty, avg_price,
         pnl_realized, pnl_unrealized, created_at)
        VALUES
        (:run_id, :xai_report_id,
         :ticker, :signal_date, :signal_price, :signal,
         :fill_date, :fill_price, :qty, :side,
         :value, :commission, :cash_after,
         :position_qty, :avg_price,
         :pnl_realized, :pnl_unrealized, NOW())
        """)

        conn.execute(insert_sql, payload)

        # =============================================================
        # 2) portfolio_positions 갱신 (행 단위)
        # =============================================================
        for ex in payload:
            update_portfolio_position(conn, ex)

        # =============================================================
        # 3) portfolio_summary 갱신 (마지막 fill_date 기준)
        # =============================================================
        last_fill_date = payload[-1]["fill_date"]
        update_portfolio_summary(conn, last_fill_date)

