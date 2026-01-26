#AI/modules/data_collector/company_fundamentals_data.py
import sys
import os
import yfinance as yf
import pandas as pd
import numpy as np
from datetime import timedelta
from typing import List
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

class FundamentalsDataCollector:
    """
    기업의 재무제표(손익계산서, 대차대조표, 현금흐름표)를 수집하고
    주요 퀀트 투자 지표(ROE, 부채비율, 이자보상배율 등)를 계산하여 DB에 저장하는 클래스
    per과 pbr은 MarketDataCollector에서 계산하여 저장됨(중복 방지)
    """

    def __init__(self, db_name: str = "db"):
        self.db_name = db_name

    def get_safe_value(self, row: pd.Series, keys: List[str]) -> float:
        """
        여러 키 후보 중 존재하는 값을 찾아 반환합니다. (결측치 처리 포함)
        """
        for k in keys:
            if k in row and pd.notna(row[k]):
                return float(row[k])
        return None

    def fetch_and_calculate_metrics(self, ticker: str):
        """
        개별 종목의 재무 데이터를 수집, 병합 및 지표를 계산합니다.
        """
        stock = yf.Ticker(ticker)
        
        # 1. 재무 데이터 가져오기 (분기 기준)
        try:
            fin_df = stock.quarterly_financials.T
            bal_df = stock.quarterly_balance_sheet.T
            cash_df = stock.quarterly_cashflow.T
        except Exception as e:
            print(f"[ERROR][{ticker}] yfinance 데이터 로드 실패: {e}")
            return pd.DataFrame()

        if fin_df.empty or bal_df.empty:
            return pd.DataFrame()

        # 2. 인덱스(날짜) 통일
        fin_df.index = pd.to_datetime(fin_df.index)
        bal_df.index = pd.to_datetime(bal_df.index)
        cash_df.index = pd.to_datetime(cash_df.index)

        # 3. 데이터프레임 병합 (Inner Join)
        merged_df = fin_df.join(bal_df, lsuffix='_fin', rsuffix='_bal', how='inner')
        merged_df = merged_df.join(cash_df, rsuffix='_cash', how='left')

        processed_data = []

        for date_idx, row in merged_df.iterrows():
            date_val = date_idx.date()

            # --- A. 기본 재무 데이터 매핑 ---
            revenue = self.get_safe_value(row, ['Total Revenue', 'Operating Revenue'])
            net_income = self.get_safe_value(row, ['Net Income', 'Net Income Common Stockholders'])
            total_assets = self.get_safe_value(row, ['Total Assets'])
            total_liabilities = self.get_safe_value(row, ['Total Liabilities Net Minority Interest', 'Total Liabilities'])
            equity = self.get_safe_value(row, ['Stockholders Equity', 'Total Equity Gross Minority Interest'])
            eps = self.get_safe_value(row, ['Basic EPS', 'Diluted EPS'])
            operating_cash_flow = self.get_safe_value(row, ['Operating Cash Flow', 'Total Cash From Operating Activities'])
            shares_issued = self.get_safe_value(row, ['Share Issued', 'Ordinary Shares Number'])

            # --- 이자보상배율 계산을 위한 항목 ---
            op_income = self.get_safe_value(row, ['Operating Income', 'EBIT'])
            int_expense = self.get_safe_value(row, ['Interest Expense', 'Interest Expense Non Operating'])

            # --- B. 파생 지표 계산 ---
            
            # 1. ROE (자기자본이익률)
            roe = None
            if net_income is not None and equity is not None and equity != 0:
                roe = net_income / equity

            # 2. 부채비율 (Debt Ratio)
            debt_ratio = None
            if total_liabilities is not None and equity is not None and equity != 0:
                debt_ratio = total_liabilities / equity

            # 3. 이자보상배율 (Interest Coverage) = 영업이익 / |이자비용|
            interest_coverage = None
            if op_income is not None and int_expense is not None:
                abs_int = abs(int_expense)
                if abs_int > 0:
                    interest_coverage = op_income / abs_int

            processed_data.append((
                str(ticker),
                date_val,
                revenue,
                net_income,
                total_assets,
                total_liabilities,
                equity,
                shares_issued,
                eps,
                roe,
                debt_ratio,
                interest_coverage,
                operating_cash_flow
            ))

        return processed_data

    def save_to_db(self, ticker: str, data: List[tuple]):
        """
        처리된 데이터를 DB에 저장(Upsert)합니다.
        """
        if not data:
            # print(f"   [{ticker}] 저장할 유효한 데이터가 없습니다.")
            return

        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()

        try:
            # [SQL 수정] interest_coverage 컬럼 추가
            insert_query = """
                INSERT INTO public.financial_statements (
                    ticker, date, revenue, net_income, total_assets, 
                    total_liabilities, equity, shares_issued, eps, roe, debt_ratio, 
                    interest_coverage, operating_cash_flow
                )
                VALUES %s
                ON CONFLICT (ticker, date) 
                DO UPDATE SET
                    revenue = EXCLUDED.revenue,
                    net_income = EXCLUDED.net_income,
                    total_assets = EXCLUDED.total_assets,
                    total_liabilities = EXCLUDED.total_liabilities,
                    equity = EXCLUDED.equity,
                    shares_issued = EXCLUDED.shares_issued,
                    eps = EXCLUDED.eps,
                    roe = EXCLUDED.roe,
                    debt_ratio = EXCLUDED.debt_ratio,
                    interest_coverage = EXCLUDED.interest_coverage,
                    operating_cash_flow = EXCLUDED.operating_cash_flow;
            """
            
            execute_values(cursor, insert_query, data)
            conn.commit()
            print(f"   [{ticker}] {len(data)}건 펀더멘털 데이터(이자보상배율 포함) 저장 완료.")
            
        except Exception as e:
            conn.rollback()
            print(f"   [{ticker}][Error] DB 저장 실패: {e}")
        finally:
            cursor.close()
            conn.close()

    def update_tickers(self, tickers: List[str]):
        """
        주어진 종목 리스트에 대해 업데이트를 수행합니다.
        """
        print(f"[Fundamentals] {len(tickers)}개 종목 재무 데이터 업데이트 시작...")
        
        for ticker in tickers:
            # print(f"   [{ticker}] 재무 정보 분석 및 수집 중...")
            try:
                data = self.fetch_and_calculate_metrics(ticker)
                self.save_to_db(ticker, data)
            except Exception as e:
                print(f"   [{ticker}][Error] 처리 중 예외 발생: {e}")

# ----------------------------------------------------------------------
# [실행 모드]
# ----------------------------------------------------------------------
if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="[수동] 기업 펀더멘털 데이터 수집기")
    parser.add_argument("tickers", nargs='*', help="수집할 종목 티커 리스트")
    parser.add_argument("--all", action="store_true", help="DB 전 종목 업데이트")
    parser.add_argument("--db", default="db", help="DB 이름")

    args = parser.parse_args()
    target_tickers = args.tickers
    
    # DB 연결 테스트 및 종목 로드
    conn = get_db_conn(args.db)
    
    if args.all:
        try:
            cur = conn.cursor()
            cur.execute("SELECT DISTINCT ticker FROM public.price_data")
            rows = cur.fetchall()
            target_tickers = [r[0] for r in rows]
            cur.close()
            print(f">> DB에서 {len(target_tickers)}개 종목을 조회했습니다.")
        except Exception as e:
            print(f"[Error] 종목 로드 실패: {e}")
            sys.exit(1)
    
    conn.close()

    if not target_tickers:
        print("\n>> 수집할 종목 코드를 입력하세요 (예: AAPL TSLA)")
        print("   (종료하려면 엔터)")
        try:
            input_str = sys.stdin.readline().strip()
            if input_str:
                target_tickers = input_str.split()
            else:
                sys.exit(0)
        except KeyboardInterrupt:
            sys.exit(0)

    if target_tickers:
        collector = FundamentalsDataCollector(db_name=args.db)
        collector.update_tickers(target_tickers)
        print("\n[완료] 작업이 끝났습니다.")