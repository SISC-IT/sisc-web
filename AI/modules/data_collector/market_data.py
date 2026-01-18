# AI/modules/data_collector/market_data.py
"""
[시장 데이터 수집기]
- yfinance 등을 사용하여 최신 주가 데이터(OHLCV)를 수집하고 DB에 저장합니다.
- 기존 daily_data_collection/main.py 의 시세 수집 로직을 담당합니다.
"""

import sys
import os
import yfinance as yf
import pandas as pd
from datetime import datetime, timedelta
from typing import List
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

def update_market_data(tickers: List[str], db_name: str = "db"):
    """
    지정된 종목들의 최신 데이터를 수집하여 DB에 업데이트합니다.
    """
    print(f"[Collector] {len(tickers)}개 종목 데이터 업데이트 시작...")
    
    conn = get_db_conn(db_name)
    cursor = conn.cursor()
    
    try:
        for ticker in tickers:
            # 1. DB에서 가장 최근 날짜 확인
            cursor.execute("SELECT MAX(date) FROM public.price_data WHERE ticker = %s", (ticker,))
            last_date = cursor.fetchone()[0]
            
            # 2. 수집 시작일 설정
            if last_date:
                start_date = (last_date + timedelta(days=1)).strftime("%Y-%m-%d")
            else:
                start_date = "2020-01-01" # 초기 데이터
                
            today = datetime.now().strftime("%Y-%m-%d")
            
            if start_date > today:
                print(f"   [{ticker}] 이미 최신 데이터입니다.")
                continue
                
            # 3. yfinance 데이터 다운로드
            # auto_adjust=False: Adj Close 컬럼을 명시적으로 요청
            print(f"   [{ticker}] 다운로드 중 ({start_date} ~ Today)...")
            try:
                df = yf.download(ticker, start=start_date, progress=False, auto_adjust=False)
            except Exception as download_err:
                print(f"   [{ticker}] 다운로드 실패: {download_err}")
                continue
            
            if df.empty:
                print(f"   [{ticker}] 새 데이터 없음.")
                continue

            # [수정] yfinance 최신 버전 호환성: MultiIndex 컬럼 평탄화
            # 예: ('Close', 'AAPL') -> 'Close'
            if isinstance(df.columns, pd.MultiIndex):
                df.columns = df.columns.get_level_values(0)
                
            # 4. 데이터 전처리 및 DB 저장
            insert_query = """
                INSERT INTO public.price_data (date, ticker, open, high, low, close, volume, adjusted_close)
                VALUES %s
                ON CONFLICT (date, ticker) DO NOTHING
            """
            
            data_to_insert = []
            
            # 컬럼 존재 여부 확인 (대소문자 이슈 방지 등을 위해 체크)
            has_adj_close = 'Adj Close' in df.columns

            for index, row in df.iterrows():
                try:
                    date_val = index.date()
                    
                    # 값 추출 헬퍼 함수 (Series인 경우 첫 번째 값 추출)
                    def get_val(col_name):
                        val = row[col_name]
                        return val.iloc[0] if isinstance(val, pd.Series) else val

                    open_val = float(get_val('Open'))
                    high_val = float(get_val('High'))
                    low_val = float(get_val('Low'))
                    close_val = float(get_val('Close'))
                    vol_val = int(get_val('Volume'))
                    
                    # Adj Close가 없으면 Close 값 사용 (Fallback)
                    if has_adj_close:
                        adj_close_val = float(get_val('Adj Close'))
                    else:
                        adj_close_val = close_val

                    data_to_insert.append((
                        date_val, ticker, open_val, high_val, low_val, close_val, vol_val, adj_close_val
                    ))
                except Exception as row_err:
                    print(f"   [{ticker}] 데이터 처리 중 건너뜀 ({index.date()}): {row_err}")
                    continue
            
            if data_to_insert:
                execute_values(cursor, insert_query, data_to_insert)
                conn.commit()
                print(f"   [{ticker}] {len(data_to_insert)}건 저장 완료.")
                
    except Exception as e:
        conn.rollback()
        print(f"[Collector][Error] 시장 데이터 업데이트 실패: {e}")
    finally:
        if cursor: cursor.close()
        if conn: conn.close()

# ----------------------------------------------------------------------
# [수동 실행 모드]
# 터미널에서 직접 실행 시 동작하는 블록입니다.
# 1) 특정 종목: python market_data.py AAPL TSLA
# 2) 전체 종목(DB 기준): python market_data.py --all
# ----------------------------------------------------------------------
if __name__ == "__main__":
    import argparse

    # 1. 터미널 인자 파서 설정
    parser = argparse.ArgumentParser(description="[수동] 주식 데이터 수집기")
    parser.add_argument("tickers", nargs='*', help="수집할 종목 티커 리스트 (예: AAPL MSFT)")
    parser.add_argument("--all", action="store_true", help="DB에 존재하는 모든 종목(Price Data) 수집")
    parser.add_argument("--db", default="db", help="사용할 DB 이름 (기본값: db)")
    
    args = parser.parse_args()
    target_tickers = args.tickers
    
    # 2. --all 옵션이 켜져 있다면 DB에서 전체 종목 로드
    if args.all:
        try:
            # selector 모듈에서 함수 가져오기 (경로 설정 이후에 import 해야 함)
            from AI.modules.finder.selector import load_all_tickers_from_db
            
            print(">> DB에서 전체 종목 리스트를 조회합니다...")
            target_tickers = load_all_tickers_from_db(verbose=True)
            print(f"[Manual Mode] 총 {len(target_tickers)}개 종목을 로드했습니다.")
            
        except ImportError:
            print("[Error] 'AI.modules.finder.selector' 모듈을 찾을 수 없습니다.")
            sys.exit(1)
        except Exception as e:
            print(f"[Error] DB 종목 로드 실패: {e}")
            sys.exit(1)

    # 3. 인자가 없고 --all도 없으면 사용자에게 직접 입력 요청
    if not target_tickers and not args.all:
        print("\n[Manual Mode] 수동 데이터 수집을 시작합니다.")
        print(">> 수집할 종목 코드를 공백으로 구분하여 입력하세요 (예: 005930.KS AAPL).")
        print(">> DB 전체 종목을 업데이트하려면 'python market_data.py --all'로 실행하세요.")
        try:
            input_str = sys.stdin.readline().strip()
            if input_str:
                target_tickers = input_str.split()
        except KeyboardInterrupt:
            print("\n취소되었습니다.")
            sys.exit(0)

    # 4. 수집 실행
    if target_tickers:
        print(f"\n>> 수집 대상: {target_tickers[:5]} ... (총 {len(target_tickers)}개)")
        update_market_data(target_tickers, db_name=args.db)
        print("\n[완료] 모든 작업이 끝났습니다.")
    else:
        print(">> 입력된 종목이 없어 종료합니다.")