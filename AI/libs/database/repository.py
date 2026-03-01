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

from AI.libs.database.connection import get_db_conn


def save_executions_to_db(fills_df: pd.DataFrame, db_name: str = "db") -> None:
    """
    [체결 내역 저장]
    Backtrade 결과(fills_df)를 'public.executions' 테이블에 저장합니다.
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
    if conn is None:
        print("[Repository][Error] DB 연결에 실패하여 체결 내역을 저장할 수 없습니다.")
        return
        
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
                row["signal_date"],  
                float(row["signal_price"]),
                str(row["signal"]),
                row["fill_date"],    
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


def save_reports_to_db(reports_tuple_list: list) -> list:
    """
    XAI 리포트를 DB에 일괄 저장하고, 생성된 ID 리스트를 반환합니다.
    """
    if not reports_tuple_list:
        return []

    conn = get_db_conn()
    if conn is None:
        return []

    try:
        cursor = conn.cursor()
        
        # INSERT 쿼리 (RETURNING id 로 생성된 PK 반환)
        insert_query = """
            INSERT INTO public.xai_reports (
                ticker, signal, price, date, report
            ) VALUES %s
            RETURNING id
        """
        
        # fetch=True 옵션을 주면 execute_values가 RETURNING 된 값들을 리스트로 모아줌
        result_ids = execute_values(
            cursor, 
            insert_query, 
            reports_tuple_list,
            fetch=True
        )
        
        conn.commit()
        
        # result_ids는 [(1,), (2,)] 형태의 튜플 리스트이므로 단일 리스트로 언패킹
        saved_ids = [row[0] for row in result_ids] if result_ids else []
        
        print(f"[Repository] XAI 리포트 {len(reports_tuple_list)}건 저장 완료 (IDs: {len(saved_ids)}개).")
        return saved_ids

    except Exception as e:
        if conn:
            conn.rollback()
        print(f"[Repository][Error] XAI 리포트 저장 중 오류 발생: {e}")
        return []
    finally:
        if conn:
            cursor.close()
            conn.close()

def get_current_position(ticker: str, target_date: str = None, initial_cash: float = 10000000, db_name: str = "db") -> dict:
    """
    [현재 포지션 조회] (업그레이드: 누적 실현손익 추가 & 미래 데이터 차단)
    특정 날짜(target_date) 이전까지의 체결 내역만 계산하여 정확한 과거 스냅샷을 만듭니다.
    """
    conn = get_db_conn(db_name)
    if conn is None:
        return {"cash": initial_cash, "qty": 0, "avg_price": 0.0, "pnl_realized_cum": 0.0}
        
    cursor = conn.cursor()
    
    # target_date가 주어지면 그 날짜 '이하'의 체결내역만 가져옵니다 (미래 데이터 훔쳐보기 방지)
    query = """
        SELECT side, qty, fill_price, commission
        FROM public.executions
        WHERE ticker = %s 
    """
    params = [ticker]
    
    if target_date:
        query += " AND fill_date <= %s "
        params.append(target_date)
        
    query += " ORDER BY fill_date ASC, created_at ASC"
    
    try:
        cursor.execute(query, tuple(params))
        rows = cursor.fetchall()
        
        current_qty = 0
        current_cash = initial_cash
        total_cost = 0.0 
        avg_price = 0.0
        pnl_realized_cum = 0.0 # 누적 실현 손익 추가
        
        for side, qty, price, commission in rows:
            qty = int(qty)
            price = float(price)
            commission = float(commission)
            trade_amount = price * qty
            
            if side == "BUY":
                cost = trade_amount + commission
                current_cash -= cost
                
                total_cost += trade_amount
                current_qty += qty
                if current_qty > 0:
                    avg_price = total_cost / current_qty
                    
            elif side == "SELL":
                revenue = trade_amount - commission
                current_cash += revenue
                
                # 실현 손익 계산: (매도단가 - 평단가) * 수량 - 수수료
                realized = ((price - avg_price) * qty) - commission
                pnl_realized_cum += realized
                
                current_qty -= qty
                if current_qty > 0:
                    total_cost = avg_price * current_qty
                else:
                    avg_price = 0.0
                    total_cost = 0.0

        return {
            "cash": current_cash,
            "qty": current_qty,
            "avg_price": avg_price,
            "pnl_realized_cum": pnl_realized_cum # 추가됨
        }
        
    except Exception as e:
        print(f"[Repository][Error] 포지션 조회 중 오류 발생: {e}")
        return {"cash": initial_cash, "qty": 0, "avg_price": 0.0, "pnl_realized_cum": 0.0}
    finally:
        cursor.close()
        conn.close()

def save_portfolio_summary(date: str, total_asset: float, cash: float, market_value: float, 
                           pnl_unrealized: float, pnl_realized_cum: float, 
                           initial_capital: float, return_rate: float, db_name: str = "db"):
    """
    [일일 마감] 계좌의 총 자산 요약본을 저장합니다. (ON CONFLICT DO UPDATE 로 덮어쓰기 지원)
    """
    conn = get_db_conn(db_name)
    if conn is None: return
    
    query = """
        INSERT INTO public.portfolio_summary 
        (date, total_asset, cash, market_value, pnl_unrealized, pnl_realized_cum, initial_capital, return_rate)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (date) DO UPDATE 
        SET total_asset = EXCLUDED.total_asset,
            cash = EXCLUDED.cash,
            market_value = EXCLUDED.market_value,
            pnl_unrealized = EXCLUDED.pnl_unrealized,
            pnl_realized_cum = EXCLUDED.pnl_realized_cum,
            return_rate = EXCLUDED.return_rate;
    """
    try:
        with conn.cursor() as cursor:
            cursor.execute(query, (date, total_asset, cash, market_value, pnl_unrealized, pnl_realized_cum, initial_capital, return_rate))
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"[Repository][Error] 요약 저장 실패: {e}")
    finally:
        conn.close()
def save_portfolio_positions(date: str, data_tuples: list, db_name: str = "db"):
    """
    [일일 마감] 현재 보유 중인 주식 스냅샷을 저장합니다.
    """
    if not data_tuples: return
        
    conn = get_db_conn(db_name)
    if conn is None: return
    
    try:
        cursor = conn.cursor()
        # 동일한 date의 기존 스냅샷을 지우고 새로 씁니다 (Backfill 중복 방지 멱등성 보장)
        cursor.execute("DELETE FROM public.portfolio_positions WHERE date = %s", (date,))
        
        # run_id (및 자동생성되는 id) 제외하고 실제 필요한 데이터만 INSERT
        insert_query = """
            INSERT INTO public.portfolio_positions 
            (date, ticker, position_qty, avg_price, current_price, market_value, pnl_unrealized, pnl_realized_cum)
            VALUES %s
        """
        execute_values(cursor, insert_query, data_tuples)
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"[Repository][Error] 포지션 저장 실패: {e}")
    finally:
        cursor.close()
        conn.close()