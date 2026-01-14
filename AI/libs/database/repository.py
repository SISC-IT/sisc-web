# AI/libs/database/repository.py
"""
[데이터 저장소 (Repository)]
- AI 파이프라인의 결과물(체결 내역, XAI 리포트 등)을 DB에 저장하는 역할을 전담합니다.
- 기존 save_executions_to_db.py 와 save_reports_to_db.py 를 통합했습니다.
"""

from typing import List, Tuple, Optional, Any
import pandas as pd
import psycopg2
from psycopg2.extras import execute_values

# 변경된 구조에 맞게 import 경로 수정 (connection.py 사용)
# 만약 connection.py가 아직 없다면 기존 get_db_conn을 임포트하거나,
# 나중에 파일명이 바뀌면 여기도 같이 수정해야 합니다.

from AI.libs.database.connection import get_db_conn


def save_executions_to_db(fills_df: pd.DataFrame, db_name: str = "db") -> None:
    """
    [체결 내역 저장]
    Backtrade 결과(fills_df)를 'public.executions' 테이블에 저장합니다.
    
    Args:
        fills_df (pd.DataFrame): 체결 내역 데이터프레임
        db_name (str): DB 연결 정보 키 (기본값: "db")
    """
    if fills_df is None or fills_df.empty:
        print("[Repository] 저장할 체결 내역이 없습니다.")
        return

    # 필수 컬럼 확인
    required_cols = {
        "run_id", "ticker", "signal_date", "signal_price", "signal",
        "fill_date", "fill_price", "qty", "side", "value",
        "commission", "cash_after", "position_qty", "avg_price",
        "pnl_realized", "pnl_unrealized"
    }
    if not required_cols.issubset(fills_df.columns):
        missing = required_cols - set(fills_df.columns)
        print(f"[Repository][Error] 체결 내역 데이터에 필수 컬럼이 누락되었습니다: {missing}")
        return

    conn = get_db_conn(db_name)
    cursor = conn.cursor()

    try:
        # INSERT 쿼리 (xai_report_id는 선택적 컬럼)
        insert_query = """
            INSERT INTO public.executions (
                run_id, xai_report_id, ticker, signal_date, signal_price, signal,
                fill_date, fill_price, qty, side, value,
                commission, cash_after, position_qty, avg_price,
                pnl_realized, pnl_unrealized, created_at
            ) VALUES %s
        """

        data_to_insert = []
        for _, row in fills_df.iterrows():
            # xai_report_id 처리 (NaN -> None)
            xai_id = row.get("xai_report_id")
            if pd.isna(xai_id):
                xai_id = None
            else:
                xai_id = int(xai_id)

            data_to_insert.append((
                str(row["run_id"]),
                xai_id,
                str(row["ticker"]),
                row["signal_date"],  # str or date
                float(row["signal_price"]),
                str(row["signal"]),
                row["fill_date"],    # str or date
                float(row["fill_price"]),
                int(row["qty"]),
                str(row["side"]),
                float(row["value"]),
                float(row["commission"]),
                float(row["cash_after"]),
                int(row["position_qty"]),
                float(row["avg_price"]),
                float(row["pnl_realized"]),
                float(row["pnl_unrealized"]),
                pd.Timestamp.now()  # created_at
            ))

        execute_values(cursor, insert_query, data_to_insert)
        conn.commit()
        print(f"[Repository] 체결 내역 {len(data_to_insert)}건 저장 완료.")

    except Exception as e:
        conn.rollback()
        print(f"[Repository][Error] 체결 내역 저장 중 오류 발생: {e}")
        raise e
    finally:
        cursor.close()
        conn.close()


def save_reports_to_db(reports: List[Tuple], db_name: str = "db") -> List[int]:
    """
    [XAI 리포트 저장]
    생성된 XAI 리포트를 'public.xai_reports' 테이블에 저장하고, 생성된 ID 리스트를 반환합니다.
    
    Args:
        reports (List[Tuple]): (ticker, signal, price, date, report_text) 형태의 튜플 리스트
        db_name (str): DB 연결 정보 키
        
    Returns:
        List[int]: 저장된 리포트들의 Primary Key (id) 리스트
    """
    if not reports:
        print("[Repository] 저장할 XAI 리포트가 없습니다.")
        return []

    conn = get_db_conn(db_name)
    cursor = conn.cursor()
    generated_ids = []

    try:
        # INSERT 쿼리 (RETURNING id 로 생성된 PK 반환)
        insert_query = """
            INSERT INTO public.xai_reports (
                ticker, signal, price, date, report_text, created_at
            ) VALUES %s
            RETURNING id
        """

        # 데이터 변환 (Tuple -> List for execute_values)
        # reports 구조: (ticker, signal, price, date, report_text)
        data_to_insert = [
            (
                r[0],  # ticker
                r[1],  # signal
                float(r[2]),  # price
                r[3],  # date
                r[4],  # report_text
                pd.Timestamp.now() # created_at
            )
            for r in reports
        ]

        execute_values(cursor, insert_query, data_to_insert, fetch=True)
        
        # RETURNING id 결과 가져오기
        rows = cursor.fetchall()
        generated_ids = [row[0] for row in rows]
        
        conn.commit()
        print(f"[Repository] XAI 리포트 {len(data_to_insert)}건 저장 완료 (IDs: {len(generated_ids)}개).")

    except Exception as e:
        conn.rollback()
        print(f"[Repository][Error] XAI 리포트 저장 중 오류 발생: {e}")
        # 오류 시 빈 리스트 반환 (호출 측에서 처리)
        return []
    finally:
        cursor.close()
        conn.close()

    return generated_ids

def get_current_position(ticker: str, initial_cash: float = 10000000, db_name: str = "db") -> dict:
    """
    [현재 포지션 조회]
    DB의 체결 내역(executions)을 기반으로 특정 종목의 현재 보유 수량, 평단가, 남은 현금을 계산합니다.
    """
    conn = get_db_conn(db_name)
    cursor = conn.cursor()
    
    # 해당 종목의 모든 체결 내역 조회 (시간순)
    query = """
        SELECT side, qty, fill_price, commission
        FROM public.executions
        WHERE ticker = %s
        ORDER BY fill_date ASC, created_at ASC
    """
    
    cursor.execute(query, (ticker,))
    rows = cursor.fetchall()
    
    current_qty = 0
    current_cash = initial_cash
    total_cost = 0.0 # 평단가 계산용 총 매수 금액
    avg_price = 0.0
    
    for side, qty, price, commission in rows:
        qty = int(qty)
        price = float(price)
        commission = float(commission)
        trade_amount = price * qty
        
        if side == "BUY":
            cost = trade_amount + commission
            current_cash -= cost
            
            # 평단가 갱신 (이동평균법)
            total_cost += trade_amount
            current_qty += qty
            if current_qty > 0:
                avg_price = total_cost / current_qty
                
        elif side == "SELL":
            revenue = trade_amount - commission
            current_cash += revenue
            
            # 매도 시 평단가는 변하지 않음, 보유 수량과 총 매수 원금만 감소
            current_qty -= qty
            total_cost = avg_price * current_qty
            
            if current_qty == 0:
                avg_price = 0.0
                total_cost = 0.0

    cursor.close()
    conn.close()
    
    return {
        "cash": current_cash,
        "qty": current_qty,
        "avg_price": avg_price
    }
