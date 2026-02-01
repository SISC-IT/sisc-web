#AI/modules/data_collector/market_data.py
import sys
import os
import yfinance as yf
import pandas as pd
from datetime import datetime, timedelta
from typing import List
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

class MarketDataCollector:
    """
    주식 종목의 시세(OHLCV) 데이터를 수집하여 DB에 적재하는 클래스
    - yfinance를 사용하여 데이터 수집
    - 수리(Repair) 모드 지원 (누락 데이터 복구)
    - 거래대금(Amount) 자동 계산
    """
    
    def __init__(self, db_name: str = "db"):
        self.db_name = db_name
        # 수집 시작 기준일 (데이터가 아예 없을 경우)
        self.FIXED_START_DATE = "2015-01-01"

    def get_start_date(self, ticker: str, repair_mode: bool) -> str:
        """
        DB를 조회하여 수집 시작 날짜를 결정합니다.
        """
        # 복구 모드이거나 강제 업데이트인 경우 고정 시작일 반환
        if repair_mode:
            return self.FIXED_START_DATE

        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT MAX(date) FROM public.price_data WHERE ticker = %s", (ticker,))
            last_date = cursor.fetchone()[0]
            
            if last_date:
                # 마지막 데이터 다음 날부터 수집
                return (last_date + timedelta(days=1)).strftime("%Y-%m-%d")
            else:
                return self.FIXED_START_DATE
        except Exception as e:
            print(f"   [Warning] 시작 날짜 조회 실패 ({ticker}): {e}")
            return self.FIXED_START_DATE
        finally:
            cursor.close()
            conn.close()

    def fetch_ohlcv(self, ticker: str, start_date: str) -> pd.DataFrame:
        """
        yfinance에서 OHLCV 데이터를 다운로드하고 전처리합니다.
        """
        try:
            # auto_adjust=False: Adj Close 별도 확보
            df = yf.download(ticker, start=start_date, progress=False, auto_adjust=False, threads=False)
            
            if df.empty:
                return pd.DataFrame()

            # MultiIndex 컬럼 평탄화 (yfinance 버전 이슈 대응)
            if isinstance(df.columns, pd.MultiIndex):
                df.columns = df.columns.get_level_values(0)
            
            # 거래대금(Amount) 계산: 종가 * 거래량 (근사치)
            # yfinance는 원화/달러 여부에 따라 단위가 다르지만, raw value 유지
            if 'Close' in df.columns and 'Volume' in df.columns:
                df['Amount'] = df['Close'] * df['Volume']
            else:
                df['Amount'] = 0

            return df
        except Exception as e:
            print(f"   [Error] {ticker} 다운로드 중 에러: {e}")
            return pd.DataFrame()

    def save_to_db(self, ticker: str, df: pd.DataFrame):
        """
        데이터프레임을 DB에 저장(Upsert)합니다.
        """
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
                adjusted_close = EXCLUDED.adjusted_close,
                amount = EXCLUDED.amount;
        """
        # repair_mode에서도 기존 값이 수정될 수 있도록 DO UPDATE로 변경하거나,
        # 원본 보존을 원하면 DO NOTHING을 유지할 수 있음. 
        # 여기서는 최신 데이터로 갱신하는 DO UPDATE 전략 채택.

        try:
            data_to_insert = []
            has_adj = 'Adj Close' in df.columns

            for index, row in df.iterrows():
                date_val = index.date()
                
                # 안전한 값 추출 (Series일 경우 첫 번째 값 사용)
                def get_val(col):
                    val = row.get(col, 0)
                    if hasattr(val, 'iloc'):
                        return float(val.iloc[0])
                    return float(val) if pd.notnull(val) else 0

                open_val = get_val('Open')
                high_val = get_val('High')
                low_val = get_val('Low')
                close_val = get_val('Close')
                vol_val = int(get_val('Volume'))
                amount_val = get_val('Amount')
                
                adj_close_val = get_val('Adj Close') if has_adj else close_val

                data_to_insert.append((
                    date_val, ticker, open_val, high_val, low_val, close_val, vol_val, adj_close_val, amount_val
                ))

            if data_to_insert:
                execute_values(cursor, insert_query, data_to_insert)
                conn.commit()
                print(f"   [{ticker}] {len(data_to_insert)}건 저장 완료.")
            else:
                print(f"   [{ticker}] 저장할 데이터가 없습니다.")

        except Exception as e:
            conn.rollback()
            print(f"   [{ticker}][Error] DB 저장 실패: {e}")
        finally:
            cursor.close()
            conn.close()

    def update_tickers(self, tickers: List[str], repair_mode: bool = False):
        """
        여러 종목에 대해 수집 작업을 수행하는 메인 로직
        """
        mode_msg = "[Repair Mode]" if repair_mode else "[Update Mode]"
        print(f"[MarketData] {mode_msg} {len(tickers)}개 종목 업데이트 시작...")

        today = datetime.now().strftime("%Y-%m-%d")

        for ticker in tickers:
            # 1. 수집 시작 날짜 결정
            start_date = self.get_start_date(ticker, repair_mode)

            # 이미 최신이면 스킵 (Repair 모드가 아닐 때만)
            if not repair_mode and start_date > today:
                print(f"   [{ticker}] 이미 최신 데이터입니다.")
                continue

            print(f"   [{ticker}] 수집 시작 ({start_date} ~ )...")
            
            # 2. 데이터 수집
            df = self.fetch_ohlcv(ticker, start_date)
            
            # 3. DB 저장
            if not df.empty:
                self.save_to_db(ticker, df)
            else:
                print(f"   [{ticker}] 수집된 데이터가 없습니다.")

# ----------------------------------------------------------------------
# [실행 모드]
# ----------------------------------------------------------------------
if __name__ == "__main__":
    import argparse
    from AI.libs.database.ticker_loader import load_all_tickers_from_db

    parser = argparse.ArgumentParser(description="[수동] 주식 데이터 수집기")
    parser.add_argument("tickers", nargs='*', help="수집할 종목 티커 리스트")
    parser.add_argument("--all", action="store_true", help="DB에 등록된 모든 종목 업데이트")
    parser.add_argument("--repair", action="store_true", help="[복구모드] 전체 기간 재수집")
    parser.add_argument("--db", default="db", help="DB 이름")
    
    args = parser.parse_args()
    target_tickers = args.tickers
    
    # --all 옵션 처리
    if args.all:
        try:
            print(">> DB에서 전체 종목 리스트를 조회합니다...")
            target_tickers = load_all_tickers_from_db(verbose=True)
        except Exception as e:
            print(f"[Error] 종목 로드 실패: {e}")
            sys.exit(1)

    # 입력이 없을 경우 인터랙티브 모드
    if not target_tickers:
        print("\n========================================================")
        print(" [Manual Mode] 주식 시장 데이터 수집기")
        print("========================================================")
        print(" 사용 예시:")
        print("  1) 특정 종목 수집  : python market_data.py AAPL TSLA")
        print("  2) 전체 종목 업데이트: python market_data.py --all")
        print("  3) 누락 데이터 복구 : python market_data.py --all --repair")
        print("========================================================")
        print(">> 수집할 종목 코드를 공백으로 구분하여 입력하세요 (종료: Enter)")
        
        try:
            input_str = sys.stdin.readline().strip()
            if input_str:
                target_tickers = input_str.split()
            else:
                sys.exit(0)
        except KeyboardInterrupt:
            sys.exit(0)

    if target_tickers:
        collector = MarketDataCollector(db_name=args.db)
        collector.update_tickers(target_tickers, repair_mode=args.repair)
        print("\n[완료] 모든 작업이 끝났습니다.")