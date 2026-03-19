#AI/modules/data_collector/crypto_data.py
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

class CryptoDataCollector:
    """
    암호화폐(Crypto)의 시세 데이터를 수집하여 DB에 적재하는 클래스
    - 대상 테이블: crypto_price_data
    - yfinance 티커 예시: BTC-USD, ETH-USD
    """
    
    def __init__(self, db_name: str = "db"):
        self.db_name = db_name
        # 암호화폐 데이터가 없을 경우 시작할 기본 날짜 (비트코인 등 주요 코인 고려)
        self.FIXED_START_DATE = "2018-01-01"

    def get_start_date(self, ticker: str, repair_mode: bool) -> str:
        """
        DB를 조회하여 수집 시작 날짜를 결정합니다.
        """
        if repair_mode:
            return self.FIXED_START_DATE

        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            # 티커에서 '-USD'를 제거하거나 그대로 사용할지 정책에 따라 다르지만,
            # 여기서는 입력받은 티커 그대로 DB에 저장한다고 가정합니다.
            cursor.execute("SELECT MAX(date) FROM public.crypto_price_data WHERE ticker = %s", (ticker,))
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

    def fetch_crypto_ohlcv(self, ticker: str, start_date: str) -> pd.DataFrame:
        """
        yfinance에서 암호화폐 OHLCV 데이터를 다운로드합니다.
        """
        try:
            # Crypto는 24/7 거래되므로 interval='1d'로 일봉 수집
            df = yf.download(ticker, start=start_date, progress=False, auto_adjust=False, threads=False)
            
            if df.empty:
                return pd.DataFrame()

            # MultiIndex 컬럼 평탄화
            if isinstance(df.columns, pd.MultiIndex):
                df.columns = df.columns.get_level_values(0)
            
            # yfinance history 데이터에는 과거 Market Cap이 포함되지 않는 경우가 많음.
            # 스키마 요구사항에 따라 컬럼은 존재해야 하므로 None으로 초기화하거나
            # 별도 로직(Close * Circulating Supply)이 필요함. 현재는 None 처리.
            if 'Market Cap' not in df.columns:
                df['Market Cap'] = None

            return df
        except Exception as e:
            print(f"   [Error] {ticker} 다운로드 중 에러: {e}")
            return pd.DataFrame()

    def save_to_db(self, ticker: str, df: pd.DataFrame):
        """
        데이터프레임을 crypto_price_data 테이블에 저장(Upsert)합니다.
        """
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()

        insert_query = """
            INSERT INTO public.crypto_price_data (
                ticker, date, open, high, low, close, volume, market_cap
            )
            VALUES %s
            ON CONFLICT (ticker, date) DO UPDATE SET
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close,
                volume = EXCLUDED.volume,
                market_cap = EXCLUDED.market_cap;
        """

        try:
            data_to_insert = []
            has_adj = 'Adj Close' in df.columns # Crypto는 보통 Close == Adj Close

            for index, row in df.iterrows():
                # Timestamp to datetime
                date_val = index.to_pydatetime()
                
                def get_val(col):
                    val = row.get(col, 0)
                    if hasattr(val, 'iloc'):
                        return float(val.iloc[0])
                    # None(Market Cap) 처리
                    if pd.isna(val) or val is None:
                        return None
                    return float(val)

                open_val = get_val('Open')
                high_val = get_val('High')
                low_val = get_val('Low')
                close_val = get_val('Close')
                vol_val = get_val('Volume')
                mkt_cap_val = get_val('Market Cap')

                data_to_insert.append((
                    ticker, date_val, open_val, high_val, low_val, close_val, vol_val, mkt_cap_val
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
        여러 암호화폐 티커에 대해 수집 작업을 수행합니다.
        """
        mode_msg = "[Repair Mode]" if repair_mode else "[Update Mode]"
        print(f"[CryptoData] {mode_msg} {len(tickers)}개 코인 업데이트 시작...")

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
            df = self.fetch_crypto_ohlcv(ticker, start_date)
            
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

    parser = argparse.ArgumentParser(description="[수동] 암호화폐 데이터 수집기")
    parser.add_argument("tickers", nargs='*', help="수집할 티커 리스트 (예: BTC-USD ETH-USD)")
    # --all 옵션 추가: BTC-USD와 ETH-USD만 대상으로 설정
    parser.add_argument("--all", action="store_true", help="주요 코인(BTC, ETH) 데이터 일괄 업데이트")
    parser.add_argument("--repair", action="store_true", help="[복구모드] 전체 기간 재수집")
    parser.add_argument("--db", default="db", help="DB 이름")
    
    args = parser.parse_args()
    target_tickers = args.tickers

    # --all 옵션 처리: BTC와 ETH만 강제 지정
    if args.all:
        target_tickers = ["BTC-USD", "ETH-USD"]
        print(">> [Info] --all 옵션 활성화: BTC-USD, ETH-USD 만 수집합니다.")

    # 입력이 없을 경우 인터랙티브 모드
    if not target_tickers:
        print("\n========================================================")
        print(" [Manual Mode] 암호화폐 데이터 수집기")
        print("========================================================")
        print(" 사용 예시:")
        print("  1) 특정 코인 수집  : python crypto_data.py BTC-USD XRP-USD")
        print("  2) 주요 코인(BTC/ETH) 업데이트 : python crypto_data.py --all")
        print("  3) 누락 데이터 복구 : python crypto_data.py --all --repair")
        print("========================================================")
        print(">> 수집할 티커를 입력하세요 (종료: Enter)")
        
        try:
            input_str = sys.stdin.readline().strip()
            if input_str:
                target_tickers = input_str.split()
            else:
                sys.exit(0)
        except KeyboardInterrupt:
            sys.exit(0)

    if target_tickers:
        collector = CryptoDataCollector(db_name=args.db)
        collector.update_tickers(target_tickers, repair_mode=args.repair)
        print("\n[완료] 작업이 끝났습니다.")