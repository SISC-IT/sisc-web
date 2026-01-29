#AI/modules/data_collector/index_data.py
import sys
import os
import yfinance as yf
import pandas as pd
from datetime import datetime, timedelta
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

class IndexDataCollector:
    """
    주요 주식 시장 지수(Benchmark Index)를 수집하여 price_data 테이블에 저장하는 클래스
    - S&P 500, NASDAQ, KOSPI, KOSDAQ 등
    - 개별 종목과 동일하게 price_data에 저장되지만, 티커명이 '^'로 시작함
    """

    def __init__(self, db_name: str = "db"):
        self.db_name = db_name
        # 관리할 주요 지수 목록 (필요에 따라 추가)
        self.INDICES = {
            '^GSPC': 'S&P 500',
            '^IXIC': 'NASDAQ Composite',
            '^DJI': 'Dow Jones Industrial Average',
            '^KS11': 'KOSPI Composite',
            '^KQ11': 'KOSDAQ Composite',
            '^RUT': 'Russell 2000'
        }
        self.FIXED_START_DATE = "2010-01-01"

    def get_start_date(self, ticker: str, repair_mode: bool) -> str:
        """DB에서 마지막 수집일을 조회"""
        if repair_mode:
            return self.FIXED_START_DATE

        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT MAX(date) FROM public.price_data WHERE ticker = %s", (ticker,))
            last_date = cursor.fetchone()[0]
            if last_date:
                return (last_date + timedelta(days=1)).strftime("%Y-%m-%d")
            return self.FIXED_START_DATE
        except Exception:
            return self.FIXED_START_DATE
        finally:
            cursor.close()
            conn.close()

    def fetch_index_data(self, ticker: str, start_date: str) -> pd.DataFrame:
        """yfinance를 통해 지수 데이터 수집"""
        try:
            # 지수는 수정주가(Adj Close) 개념이 명확하지 않으나 통일성을 위해 수집
            df = yf.download(ticker, start=start_date, progress=False, auto_adjust=False, threads=False)
            if df.empty:
                return pd.DataFrame()

            if isinstance(df.columns, pd.MultiIndex):
                df.columns = df.columns.get_level_values(0)

            # 거래대금 계산 (지수는 Volume이 0이거나 없는 경우가 많음)
            if 'Close' in df.columns and 'Volume' in df.columns:
                # 지수의 'Volume'은 거래량이 아닌 경우가 많아(계약수 등) 단순 참고용
                df['Amount'] = df['Close'] * df['Volume'] 
            else:
                df['Amount'] = 0
            
            return df
        except Exception as e:
            print(f"   [Error] 지수 {ticker} 수집 실패: {e}")
            return pd.DataFrame()

    def save_to_db(self, ticker: str, df: pd.DataFrame):
        """DB 저장 (market_data와 동일한 price_data 테이블 사용)"""
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()

        insert_query = """
            INSERT INTO public.price_data (
                date, ticker, open, high, low, close, volume, adjusted_close, amount
            )
            VALUES %s
            ON CONFLICT (date, ticker) DO UPDATE SET
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close,
                volume = EXCLUDED.volume,
                adjusted_close = EXCLUDED.adjusted_close;
        """

        try:
            data_to_insert = []
            has_adj = 'Adj Close' in df.columns
            
            for index, row in df.iterrows():
                try:
                    date_val = index.date()
                    
                    def get_val(col):
                        val = row.get(col, 0)
                        if hasattr(val, 'iloc'): return float(val.iloc[0])
                        return float(val) if pd.notnull(val) else 0

                    open_val = get_val('Open')
                    high_val = get_val('High')
                    low_val = get_val('Low')
                    close_val = get_val('Close')
                    vol_val = int(get_val('Volume'))
                    adj_close_val = get_val('Adj Close') if has_adj else close_val
                    amount_val = get_val('Amount')

                    data_to_insert.append((
                        date_val, ticker, open_val, high_val, low_val, 
                        close_val, vol_val, adj_close_val, amount_val
                    ))
                except:
                    continue

            if data_to_insert:
                execute_values(cursor, insert_query, data_to_insert)
                conn.commit()
                print(f"   [{self.INDICES[ticker]}] {len(data_to_insert)}일치 저장 완료.")
            else:
                print(f"   [{self.INDICES[ticker]}] 최신 데이터임.")

        except Exception as e:
            conn.rollback()
            print(f"   [Error] DB 저장 오류: {e}")
        finally:
            cursor.close()
            conn.close()

    def run(self, repair_mode: bool = False):
        """전체 지수 업데이트 실행"""
        mode_msg = "[Repair Mode]" if repair_mode else "[Update Mode]"
        print(f"[IndexCollector] {mode_msg} 주요 시장 지수 업데이트 시작...")
        
        today = datetime.now().strftime("%Y-%m-%d")

        for ticker, name in self.INDICES.items():
            start_date = self.get_start_date(ticker, repair_mode)
            
            if not repair_mode and start_date > today:
                print(f"   [{name}] 이미 최신입니다.")
                continue

            print(f"   [{name}({ticker})] 수집 시작 ({start_date} ~ )...")
            df = self.fetch_index_data(ticker, start_date)
            if not df.empty:
                self.save_to_db(ticker, df)

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--repair", action="store_true", help="전체 기간 재수집")
    args = parser.parse_args()

    collector = IndexDataCollector()
    collector.run(repair_mode=args.repair)