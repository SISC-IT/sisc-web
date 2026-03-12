# AI/modules/data_collector/company_fundamentals_data.py
import sys
import os
import time
import requests
import yfinance as yf
import pandas as pd
from typing import List
from datetime import datetime
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn
from AI.libs.database.ticker_loader import load_all_tickers_from_db

class FundamentalsDataCollector:
    """
    [하이브리드 재무 데이터 수집기 - Docker 완벽 대응]
    1. YFinance 엔진: 빈 DB를 빠르게 채우기 위해 4년 치 연간 데이터를 수집합니다. (제한 없음)
    2. FMP API 엔진: 매일 80개씩 10년 치 연간 데이터로 점진적 업그레이드(Backfill)를 수행합니다.
    * DB의 stock_info.fmp_completed 상태값을 사용하여 신규 상장주 무한 루프를 방지합니다.
    """

    def __init__(self, db_name: str = "db"):
        self.db_name = db_name
        self.api_key = os.getenv("FMP_API_KEY")

    def get_safe_value(self, row: pd.Series, keys: List[str]) -> float:
        for k in keys:
            if k in row and pd.notna(row[k]):
                return float(row[k])
        return None

    # ==========================================
    # 🚀 엔진 1: YFinance (최근 4년 치 빠른 수집)
    # ==========================================
    def fetch_yf_metrics(self, ticker: str) -> List[tuple]:
        stock = yf.Ticker(ticker)
        try:
            fin_df = stock.financials.T
            bal_df = stock.balance_sheet.T
            cash_df = stock.cashflow.T
        except Exception:
            return []

        if fin_df.empty or bal_df.empty:
            return []

        fin_df.index = pd.to_datetime(fin_df.index)
        bal_df.index = pd.to_datetime(bal_df.index)
        cash_df.index = pd.to_datetime(cash_df.index)

        merged_df = fin_df.join(bal_df, lsuffix='_fin', rsuffix='_bal', how='inner')
        merged_df = merged_df.join(cash_df, rsuffix='_cash', how='left')

        processed_data = []
        for date_idx, row in merged_df.iterrows():
            date_val = date_idx.date()

            revenue = self.get_safe_value(row, ['Total Revenue', 'Operating Revenue'])
            net_income = self.get_safe_value(row, ['Net Income', 'Net Income Common Stockholders'])
            total_assets = self.get_safe_value(row, ['Total Assets'])
            total_liabilities = self.get_safe_value(row, ['Total Liabilities Net Minority Interest', 'Total Liabilities'])
            equity = self.get_safe_value(row, ['Stockholders Equity', 'Total Equity Gross Minority Interest'])
            eps = self.get_safe_value(row, ['Basic EPS', 'Diluted EPS'])
            operating_cash_flow = self.get_safe_value(row, ['Operating Cash Flow', 'Total Cash From Operating Activities'])
            shares_issued = self.get_safe_value(row, ['Share Issued', 'Ordinary Shares Number'])

            op_income = self.get_safe_value(row, ['Operating Income', 'EBIT'])
            int_expense = self.get_safe_value(row, ['Interest Expense', 'Interest Expense Non Operating'])

            roe = (net_income / equity) if net_income and equity else None
            debt_ratio = (total_liabilities / equity) if total_liabilities and equity else None
            interest_coverage = (op_income / abs(int_expense)) if op_income and int_expense and abs(int_expense) > 0 else None

            processed_data.append((
                str(ticker), date_val, revenue, net_income, total_assets, total_liabilities,
                equity, shares_issued, eps, roe, debt_ratio, interest_coverage, operating_cash_flow
            ))
        return processed_data

    # ==========================================
    # 💎 엔진 2: FMP API (과거 10년 치 고급 수집)
    # ==========================================
    def fetch_fmp_metrics(self, ticker: str) -> List[tuple]:
        if not self.api_key: return []
        base_url = "https://financialmodelingprep.com/api/v3"
        limit = 10
        
        try:
            inc_resp = requests.get(f"{base_url}/income-statement/{ticker}?limit={limit}&apikey={self.api_key}")
            bal_resp = requests.get(f"{base_url}/balance-sheet-statement/{ticker}?limit={limit}&apikey={self.api_key}")
            cf_resp = requests.get(f"{base_url}/cash-flow-statement/{ticker}?limit={limit}&apikey={self.api_key}")

            inc_data, bal_data, cf_data = inc_resp.json(), bal_resp.json(), cf_resp.json()

            if not inc_data or (isinstance(inc_data, dict) and 'Error Message' in inc_data):
                return []

            df_inc, df_bal, df_cf = pd.DataFrame(inc_data), pd.DataFrame(bal_data), pd.DataFrame(cf_data)
            if df_inc.empty or df_bal.empty or df_cf.empty: return []

            df_merged = pd.merge(df_inc, df_bal, on='date', how='inner', suffixes=('', '_bal'))
            df_merged = pd.merge(df_merged, df_cf, on='date', how='inner', suffixes=('', '_cf'))
        except:
            return []

        processed_data = []
        for _, row in df_merged.iterrows():
            try: date_val = datetime.strptime(row['date'], '%Y-%m-%d').date()
            except: continue

            revenue = float(row.get('revenue')) if pd.notna(row.get('revenue')) else None
            net_income = float(row.get('netIncome')) if pd.notna(row.get('netIncome')) else None
            total_assets = float(row.get('totalAssets')) if pd.notna(row.get('totalAssets')) else None
            total_liabilities = float(row.get('totalLiabilities')) if pd.notna(row.get('totalLiabilities')) else None
            equity = float(row.get('totalStockholdersEquity', row.get('totalEquity'))) if pd.notna(row.get('totalStockholdersEquity', row.get('totalEquity'))) else None
            eps = float(row.get('eps')) if pd.notna(row.get('eps')) else None
            shares_issued = float(row.get('weightedAverageShsOutDil', row.get('weightedAverageShsOut'))) if pd.notna(row.get('weightedAverageShsOutDil', row.get('weightedAverageShsOut'))) else None
            operating_cash_flow = float(row.get('operatingCashFlow')) if pd.notna(row.get('operatingCashFlow')) else None
            
            op_income = row.get('operatingIncome')
            int_expense = row.get('interestExpense')

            roe = (net_income / equity) if net_income and equity else None
            debt_ratio = (total_liabilities / equity) if total_liabilities and equity else None
            interest_coverage = (float(op_income) / abs(float(int_expense))) if pd.notna(op_income) and pd.notna(int_expense) and abs(float(int_expense)) > 0 else None

            processed_data.append((
                str(ticker), date_val, revenue, net_income, total_assets, total_liabilities,
                equity, shares_issued, eps, roe, debt_ratio, interest_coverage, operating_cash_flow
            ))
        return processed_data

    # ==========================================
    # 💾 DB 저장 및 상태 관리 로직
    # ==========================================
    def save_to_db(self, ticker: str, data: List[tuple]):
        if not data: return
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            insert_query = """
                INSERT INTO public.company_fundamentals (
                    ticker, date, revenue, net_income, total_assets, 
                    total_liabilities, equity, shares_issued, eps, roe, debt_ratio, 
                    interest_coverage, operating_cash_flow
                )
                VALUES %s
                ON CONFLICT (ticker, date) DO UPDATE SET
                    revenue = EXCLUDED.revenue, net_income = EXCLUDED.net_income,
                    total_assets = EXCLUDED.total_assets, total_liabilities = EXCLUDED.total_liabilities,
                    equity = EXCLUDED.equity, shares_issued = EXCLUDED.shares_issued,
                    eps = EXCLUDED.eps, roe = EXCLUDED.roe, debt_ratio = EXCLUDED.debt_ratio,
                    interest_coverage = EXCLUDED.interest_coverage, operating_cash_flow = EXCLUDED.operating_cash_flow;
            """
            execute_values(cursor, insert_query, data)
            conn.commit()
        except Exception as e:
            conn.rollback()
            print(f"   [{ticker}][Error] DB 저장 실패: {e}")
        finally:
            cursor.close()
            conn.close()

    def get_fmp_targets(self, tickers: List[str], limit: int = 80) -> List[str]:
        """
        stock_info 테이블에서 fmp_completed가 FALSE(또는 NULL)인 종목만 limit 개수만큼 추려옵니다.
        """
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            query = """
                SELECT ticker FROM public.stock_info 
                WHERE fmp_completed IS NOT TRUE
                AND ticker = ANY(%s);
            """
            cursor.execute(query, (tickers,))
            uncompleted = [row[0] for row in cursor.fetchall()]
            return uncompleted[:limit]
        except Exception as e:
            print(f"[Error] FMP 타겟 DB 조회 실패: {e}")
            return []
        finally:
            cursor.close()
            conn.close()

    def mark_fmp_completed(self, ticker: str):
        """
        FMP 수집이 완료된(또는 시도한) 종목은 stock_info 테이블에 TRUE로 영구 도장을 찍습니다.
        """
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            query = "UPDATE public.stock_info SET fmp_completed = TRUE WHERE ticker = %s;"
            cursor.execute(query, (ticker,))
            conn.commit()
        except Exception as e:
            conn.rollback()
            print(f"   [{ticker}][Error] 완료 상태 업데이트 실패: {e}")
        finally:
            cursor.close()
            conn.close()

    # ==========================================
    # 🔄 메인 업데이트 로직 (YF -> FMP)
    # ==========================================
    def update_tickers(self, tickers: List[str]):
        print(f"\n[Fundamentals] 총 {len(tickers)}개 종목 하이브리드 수집 시작...")

        # 1. DB에 이미 데이터가 있는 종목 확인 (yfinance 중복 방지)
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        cursor.execute("SELECT DISTINCT ticker FROM public.company_fundamentals;")
        db_filled_tickers = set(row[0] for row in cursor.fetchall())
        conn.close()

        # [단계 1] 아예 데이터가 없는 종목은 YFinance로 빠르게 베이스라인 채우기
        yf_targets = [t for t in tickers if t not in db_filled_tickers]
        if yf_targets:
            print(f" >> [Phase 1] DB에 없는 {len(yf_targets)}개 종목을 yfinance(4년 치)로 우선 채웁니다.")
            for i, ticker in enumerate(yf_targets):
                try:
                    data = self.fetch_yf_metrics(ticker)
                    if data: self.save_to_db(ticker, data)
                    if i % 10 == 0 and i > 0: print(f"    ... {i}개 yf 수집 완료")
                    time.sleep(0.5) # yf 과부하 방지
                except Exception as e:
                    print(f"   [{ticker}] yf 수집 에러: {e}")
            print(" >> [Phase 1] yfinance 베이스라인 수집 완료!\n")

        # [단계 2] 매일 80개씩 FMP API 10년 치 업그레이드
        if not self.api_key:
            print("🚨 FMP_API_KEY가 없어 10년 치 업그레이드는 건너뜁니다.")
            return

        fmp_targets = self.get_fmp_targets(tickers, limit=80)

        if not fmp_targets:
            print("🌟 [Phase 2] 모든 종목이 이미 FMP 10년 치로 업그레이드되어 있습니다! (수집 스킵)")
            return

        print(f" >> [Phase 2] {len(fmp_targets)}개 종목을 FMP API(10년 치)로 업그레이드합니다.")
        success_count = 0
        for ticker in fmp_targets:
            try:
                data = self.fetch_fmp_metrics(ticker)
                if data:
                    self.save_to_db(ticker, data)
                
                # 수집을 시도했으므로 무조건 완료 도장 찍기 (신규 상장주 무한루프 방지)
                self.mark_fmp_completed(ticker)
                success_count += 1
            except Exception as e:
                print(f"   [{ticker}] FMP 수집 에러: {e}")

        print(f" >> [Phase 2] 오늘 할당량 끝! ({success_count}개 종목 FMP 업그레이드 및 상태 저장 완료)")

# ----------------------------------------------------------------------
if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="하이브리드 펀더멘털 데이터 수집기")
    parser.add_argument("tickers", nargs='*', help="수집할 종목")
    parser.add_argument("--all", action="store_true", help="DB 전 종목 업데이트")
    parser.add_argument("--db", default="db", help="DB 이름")
    args = parser.parse_args()
    target_tickers = args.tickers
    
    if args.all:
        target_tickers = load_all_tickers_from_db(verbose=False)
    
    if target_tickers:
        collector = FundamentalsDataCollector(db_name=args.db)
        collector.update_tickers(target_tickers)