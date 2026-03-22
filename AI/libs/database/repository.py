# AI/libs/database/repository.py
"""
[데이터 저장소 (Repository)]
- AI 파이프라인의 결과물(체결 내역, XAI 리포트 등)을 DB에 저장하고 조회하는 역할을 전담합니다.
- 기존의 개별 함수들을 PortfolioRepository 클래스로 통합하여 응집도를 높이고 DB 커넥션 관리를 최적화했습니다.
"""

from typing import List, Tuple, Optional, Any, Dict
import pandas as pd
import psycopg2
from psycopg2.extras import execute_values

# 기존 DB 커넥션 함수 임포트
from AI.libs.database.connection import get_db_conn


class PortfolioRepository:
    """
    [포트폴리오 및 체결 내역 데이터 저장소 클래스]
    DB와의 상호작용(조회 및 저장)을 캡슐화하여 제공합니다.
    """

    def __init__(self, db_name: str = "db"):
        """
        [초기화 메서드]
        객체 생성 시 사용할 데이터베이스의 이름을 설정합니다.
        
        Args:
            db_name (str): 접근할 DB의 이름 (기본값: "db"). 
                           테스트 시에는 "test_db" 등으로 변경하여 주입(의존성 주입)할 수 있습니다.
        """
        self.db_name = db_name

    def get_latest_total_asset(self, target_date: str, default_asset: float = 100_000_000) -> float:
        """
        [동적 자산 배분용] 특정 날짜 이전의 가장 최근 마감 총자산을 조회합니다.
        """
        from AI.libs.database.connection import get_db_conn # 필요시 상단에 임포트
        
        conn = self._get_connection()
        if conn is None:
            return default_asset
        try:
            with conn.cursor() as cur:
                # target_date 이전의 날짜 중 가장 최근 하루의 total_asset을 가져옵니다.
                query = """
                    SELECT total_asset 
                    FROM public.portfolio_summary 
                    WHERE date < %s 
                    ORDER BY date DESC 
                    LIMIT 1
                """
                cur.execute(query, (target_date,))
                result = cur.fetchone()
                
                if result and result[0] is not None:
                    return float(result[0])
        except Exception as e:
            print(f"[Repository Error] 전일 총자산 조회 실패: {e}")
        finally:
            if conn:
                conn.close()
                
        # 과거 데이터가 없거나 에러가 나면 기본 자본금 반환
        return default_asset

    def _get_connection(self):
        """
        [내부 헬퍼 메서드]
        현재 인스턴스에 설정된 db_name을 기반으로 DB 커넥션을 맺고 반환합니다.
        
        Returns:
            psycopg2 connection 객체 또는 None (연결 실패 시)
        """
        return get_db_conn(self.db_name)

    def get_current_position(self, ticker: str, target_date: str = None, initial_cash: float = 10000) -> Dict[str, Any]:
        """
        [현재 포지션 조회] 
        특정 날짜(target_date) 이전까지의 체결 내역만 계산하여 정확한 과거 스냅샷(보유 수량, 평단가, 잔고 등)을 만듭니다.
        
        Args:
            ticker (str): 조회할 주식의 티커 심볼 (예: 'AAPL')
            target_date (str, optional): 미래 데이터 훔쳐보기(Look-ahead bias)를 방지하기 위한 기준 날짜 (YYYY-MM-DD)
            initial_cash (float): 초기 자본금 (기본값: 만 달러)
            
        Returns:
            dict: 현금 잔고(cash), 보유 수량(qty), 평균 단가(avg_price), 누적 실현 손익(pnl_realized_cum) 정보를 담은 딕셔너리
        """
        conn = self._get_connection()
        # DB 연결 실패 시 기본 상태 반환
        if conn is None:
            return {"cash": initial_cash, "qty": 0, "avg_price": 0.0, "pnl_realized_cum": 0.0}
            
        cursor = conn.cursor()
        
        # 기본 쿼리: 특정 티커의 거래 내역을 모두 가져옴
        query = """
            SELECT side, qty, fill_price, commission
            FROM public.executions
            WHERE ticker = %s 
        """
        params = [ticker]
        
        # target_date가 주어지면 그 날짜 '이하'의 체결내역만 필터링하여 미래 데이터를 차단합니다.
        if target_date:
            query += " AND fill_date <= %s "
            params.append(target_date)
            
        # 시간순으로 정렬하여 정확한 롤링 계산을 수행합니다.
        query += " ORDER BY fill_date ASC, created_at ASC"
        
        try:
            cursor.execute(query, tuple(params))
            rows = cursor.fetchall()
            
            # 계산을 위한 상태 변수 초기화
            current_qty = 0
            current_cash = initial_cash
            total_cost = 0.0 
            avg_price = 0.0
            pnl_realized_cum = 0.0 
            
            # 조회된 거래 내역을 순회하며 포지션 및 잔고 업데이트
            for side, qty, price, commission in rows:
                qty = int(qty)
                price = float(price)
                commission = float(commission)
                trade_amount = price * qty
                
                if side == "BUY":
                    # 매수 시: 자금 차감 및 평균 단가 계산
                    cost = trade_amount + commission
                    current_cash -= cost
                    
                    total_cost += trade_amount
                    current_qty += qty
                    if current_qty > 0:
                        avg_price = total_cost / current_qty
                        
                elif side == "SELL":
                    # 매도 시: 수익금 추가 및 실현 손익 계산
                    revenue = trade_amount - commission
                    current_cash += revenue
                    
                    # 실현 손익 = (매도단가 - 평단가) * 수량 - 수수료
                    realized = ((price - avg_price) * qty) - commission
                    pnl_realized_cum += realized
                    
                    current_qty -= qty
                    # 잔여 수량이 있으면 총 원금(total_cost) 재계산, 모두 팔았으면 단가 초기화
                    if current_qty > 0:
                        total_cost = avg_price * current_qty
                    else:
                        avg_price = 0.0
                        total_cost = 0.0

            return {
                "cash": current_cash,
                "qty": current_qty,
                "avg_price": avg_price,
                "pnl_realized_cum": pnl_realized_cum
            }
            
        except Exception as e:
            print(f"[PortfolioRepository][Error] 포지션 조회 중 오류 발생: {e}")
            return {"cash": initial_cash, "qty": 0, "avg_price": 0.0, "pnl_realized_cum": 0.0}
        finally:
            cursor.close()
            conn.close()

    def get_current_cash(self, target_date: str = None, initial_cash: float = 10000000) -> float:
        """
        [현재 포트폴리오 현금 조회] 
        포트폴리오 요약 테이블에서 target_date 이전의 가장 최근 현금 잔고를 조회합니다.
        
        Args:
            target_date (str, optional): 기준 날짜
            initial_cash (float): 내역이 없을 경우 반환할 초기 자본금
            
        Returns:
            float: 계산된 현재 현금 잔고
        """
        conn = self._get_connection()
        if conn is None:
            return initial_cash
            
        cursor = conn.cursor()
        
        # 기준일 이전의 가장 최근 마감 데이터를 가져오는 서브쿼리 활용
        query = """
            SELECT cash 
            FROM public.portfolio_summary 
            WHERE date = (SELECT MAX(date) FROM public.portfolio_summary WHERE date < %s)
            LIMIT 1;
        """
        
        try:
            cursor.execute(query, (target_date,))
            result = cursor.fetchone()
            if result:
                return float(result[0])
            else:
                return initial_cash
        except Exception as e:
            print(f"[PortfolioRepository][Error] 포트폴리오 현금 조회 중 오류 발생: {e}")
            return initial_cash
        finally:
            cursor.close()
            conn.close()

    def save_executions_to_db(self, fills_df: pd.DataFrame) -> None:
        """
        [체결 내역 저장]
        백테스트 또는 라이브 트레이딩의 결과(DataFrame)를 'public.executions' 테이블에 일괄 저장합니다.
        
        Args:
            fills_df (pd.DataFrame): 체결 내역이 담긴 데이터프레임
        """
        if fills_df is None or fills_df.empty:
            print("[PortfolioRepository] 저장할 체결 내역이 없습니다.")
            return

        # 저장에 필요한 필수 컬럼들이 데이터프레임에 존재하는지 검증 (방어적 프로그래밍)
        required_cols = {
            "run_id", "ticker", "signal_date", "signal_price", "signal",
            "fill_date", "fill_price", "qty", "side", "value",
            "commission", "cash_after", "position_qty", "avg_price",
            "pnl_realized", "pnl_unrealized"
        }
        
        if not required_cols.issubset(fills_df.columns):
            missing = required_cols - set(fills_df.columns)
            print(f"[PortfolioRepository][Error] 체결 내역 데이터에 필수 컬럼이 누락되었습니다: {missing}")
            return

        conn = self._get_connection()
        if conn is None:
            print("[PortfolioRepository][Error] DB 연결에 실패하여 체결 내역을 저장할 수 없습니다.")
            return
            
        cursor = conn.cursor()

        try:
            # 다량의 데이터를 빠르게 넣기 위한 INSERT 구문 준비
            insert_query = """
                INSERT INTO public.executions (
                    run_id, xai_report_id, ticker, signal_date, signal_price, signal,
                    fill_date, fill_price, qty, side, value,
                    commission, cash_after, position_qty, avg_price,
                    pnl_realized, pnl_unrealized, created_at
                ) VALUES %s
            """

            # DataFrame의 각 행을 튜플 형태로 변환
            data_to_insert = []
            for _, row in fills_df.iterrows():
                # XAI 리포트 ID가 NaN(결측치)일 경우 DB의 NULL로 매핑되도록 처리
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
                    pd.Timestamp.now()  # 삽입 시점의 서버 시간을 created_at으로 사용
                ))

            # psycopg2의 execute_values를 사용하여 일괄(Batch) INSERT 실행 -> 속도 대폭 향상
            execute_values(cursor, insert_query, data_to_insert)
            conn.commit()
            print(f"[PortfolioRepository] 체결 내역 {len(data_to_insert)}건 저장 완료.")

        except Exception as e:
            conn.rollback() # 에러 발생 시 변경사항 롤백
            print(f"[PortfolioRepository][Error] 체결 내역 저장 중 오류 발생: {e}")
            raise e
        finally:
            cursor.close()
            conn.close()

    def save_reports_to_db(self, reports_tuple_list: list) -> list:
        """
        [XAI 리포트 일괄 저장]
        설명 가능한 AI(XAI) 분석 리포트들을 DB에 저장하고, 생성된 Primary Key(ID) 리스트를 반환합니다.
        
        Args:
            reports_tuple_list (list): (ticker, signal, price, date, report_json) 형태의 튜플 리스트
            
        Returns:
            list: 생성된 xai_report 레코드들의 id 목록 (체결 내역과 외래키로 맵핑하기 위함)
        """
        if not reports_tuple_list:
            return []

        conn = self._get_connection()
        if conn is None:
            return []

        try:
            cursor = conn.cursor()
            
            # RETURNING id 절을 사용하여 INSERT 후 자동 생성된 PK를 반환받습니다.
            insert_query = """
                INSERT INTO public.xai_reports (
                    ticker, signal, price, date, report
                ) VALUES %s
                RETURNING id
            """
            
            # fetch=True 옵션으로 execute_values 실행 시 RETURNING 결과를 리스트 형태로 모아줍니다.
            result_ids = execute_values(
                cursor, 
                insert_query, 
                reports_tuple_list,
                fetch=True
            )
            
            conn.commit()
            
            # 반환된 result_ids는 [(1,), (2,), ...] 형태이므로 단일 리스트 [1, 2, ...]로 평탄화(Unpacking) 합니다.
            saved_ids = [row[0] for row in result_ids] if result_ids else []
            
            print(f"[PortfolioRepository] XAI 리포트 {len(reports_tuple_list)}건 저장 완료 (IDs: {len(saved_ids)}개).")
            return saved_ids

        except Exception as e:
            conn.rollback()
            print(f"[PortfolioRepository][Error] XAI 리포트 저장 중 오류 발생: {e}")
            return []
        finally:
            cursor.close()
            conn.close()

    def save_portfolio_summary(self, date: str, total_asset: float, cash: float, market_value: float, 
                               pnl_unrealized: float, pnl_realized_cum: float, 
                               initial_capital: float, return_rate: float):
        """
        [일일 마감 요약 저장]
        계좌의 특정 일자 총 자산 요약본을 저장합니다.
        동일 일자에 대해 재실행될 경우(Backfill) 기존 데이터를 덮어씌웁니다(Upsert).
        """
        conn = self._get_connection()
        if conn is None: 
            return
            
        # ON CONFLICT 구문을 사용하여 멱등성(Idempotency, 여러 번 실행해도 결과가 같음)을 보장합니다.
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
            print(f"[PortfolioRepository] {date} 일자 포트폴리오 요약 저장 완료.")
        except Exception as e:
            conn.rollback()
            print(f"[PortfolioRepository][Error] 요약 저장 실패: {e}")
        finally:
            conn.close()

    def save_portfolio_positions(self, date: str, data_tuples: list):
        """
        [일일 마감 포지션 스냅샷 저장]
        특정 일자에 현재 보유 중인 주식들의 스냅샷(세부 내역)을 저장합니다.
        
        Args:
            date (str): 마감 일자
            data_tuples (list): 저장할 포지션 정보 튜플들의 리스트
        """
        if not data_tuples: 
            return
            
        conn = self._get_connection()
        if conn is None: 
            return
        
        try:
            cursor = conn.cursor()
            # 동일한 date의 기존 스냅샷을 우선 삭제하여, 중복 저장을 방지하고 최신 상태로 덮어씁니다.
            cursor.execute("DELETE FROM public.portfolio_positions WHERE date = %s", (date,))
            
            insert_query = """
                INSERT INTO public.portfolio_positions 
                (date, ticker, position_qty, avg_price, current_price, market_value, pnl_unrealized, pnl_realized_cum)
                VALUES %s
            """
            execute_values(cursor, insert_query, data_tuples)
            conn.commit()
            print(f"[PortfolioRepository] {date} 일자 보유 포지션 {len(data_tuples)}건 저장 완료.")
        except Exception as e:
            conn.rollback()
            print(f"[PortfolioRepository][Error] 포지션 저장 실패: {e}")
        finally:
            cursor.close()
            conn.close()