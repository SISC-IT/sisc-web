# AI/modules/collector/market_data.py

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

# repair_mode 인자 추가
def update_market_data(tickers: List[str], db_name: str = "db", repair_mode: bool = False):
    """
    지정된 종목들의 데이터를 수집하여 DB에 업데이트합니다.
    
    Args:
        tickers: 종목 코드 리스트
        db_name: DB 설정 이름
        repair_mode: True일 경우, DB의 마지막 날짜를 무시하고 2015년부터 전체 재수집 (누락 데이터 복구용)
    """
    mode_msg = "[Repair Mode]" if repair_mode else "[Update Mode]"
    print(f"[Collector]{mode_msg} {len(tickers)}개 종목 데이터 작업 시작...")
    
    conn = get_db_conn(db_name)
    cursor = conn.cursor()
    
    # [설정] 전체 복구 시 시작할 기준 연도
    FIXED_START_DATE = "2015-01-01"
    
    try:
        for ticker in tickers:
            start_date = FIXED_START_DATE
            
            # 1. 일반 모드일 때만 DB에서 마지막 날짜 체크
            if not repair_mode:
                cursor.execute("SELECT MAX(date) FROM public.price_data WHERE ticker = %s", (ticker,))
                last_date = cursor.fetchone()[0]
                
                if last_date:
                    start_date = (last_date + timedelta(days=1)).strftime("%Y-%m-%d")
                else:
                    start_date = FIXED_START_DATE # 데이터 없으면 초기값
            
            # 2. 오늘 날짜와 비교
            today = datetime.now().strftime("%Y-%m-%d")
            
            if start_date > today:
                print(f"   [{ticker}] 이미 최신 데이터입니다.")
                continue
                
            # 3. yfinance 데이터 다운로드
            # repair_mode일 때는 2015년부터 오늘까지 전부 다운로드 (이미 있는건 DB가 무시함)
            print(f"   [{ticker}] 수집 중 ({start_date} ~ Today)...")
            try:
                # auto_adjust=False: Adj Close 확보, threads=False: 안정성 우선
                df = yf.download(ticker, start=start_date, progress=False, auto_adjust=False, threads=False)
            except Exception as download_err:
                print(f"   [{ticker}] 다운로드 실패: {download_err}")
                continue
            
            if df.empty:
                print(f"   [{ticker}] 데이터 없음.")
                continue

            # MultiIndex 컬럼 평탄화
            if isinstance(df.columns, pd.MultiIndex):
                df.columns = df.columns.get_level_values(0)
                
            # 4. DB 저장
            insert_query = """
                INSERT INTO public.price_data (date, ticker, open, high, low, close, volume, adjusted_close)
                VALUES %s
                ON CONFLICT (date, ticker) DO NOTHING
            """
            # ON CONFLICT ... DO NOTHING 덕분에
            # Repair 모드에서 중복 데이터를 넣으려 해도 에러 없이 무시되고,
            # '비어있는 날짜'만 정상적으로 INSERT 됩니다.
            
            data_to_insert = []
            has_adj_close = 'Adj Close' in df.columns

            for index, row in df.iterrows():
                try:
                    date_val = index.date()
                    
                    def get_val(col_name):
                        val = row[col_name]
                        return val.iloc[0] if isinstance(val, pd.Series) else val

                    open_val = float(get_val('Open'))
                    high_val = float(get_val('High'))
                    low_val = float(get_val('Low'))
                    close_val = float(get_val('Close'))
                    vol_val = int(get_val('Volume'))
                    
                    if has_adj_close:
                        adj_close_val = float(get_val('Adj Close'))
                    else:
                        adj_close_val = close_val

                    data_to_insert.append((
                        date_val, ticker, open_val, high_val, low_val, close_val, vol_val, adj_close_val
                    ))
                except Exception:
                    continue
            
            if data_to_insert:
                execute_values(cursor, insert_query, data_to_insert)
                conn.commit()
                # Repair 모드일 땐 중복은 0건으로 잡히므로, 실제로 들어간 갯수를 정확히 알긴 어렵지만
                # execute_values는 에러 없이 수행됩니다.
                print(f"   [{ticker}] 처리 완료 (수집: {len(data_to_insert)}건).")
                
    except Exception as e:
        conn.rollback()
        print(f"[Collector][Error] 실패: {e}")
    finally:
        if cursor: cursor.close()
        if conn: conn.close()

# ----------------------------------------------------------------------
# [수동 실행 모드]
# ----------------------------------------------------------------------
if __name__ == "__main__":
    import argparse

    # 1. 터미널 인자 파서 설정
    parser = argparse.ArgumentParser(description="[수동] 주식 데이터 수집기")
    parser.add_argument("tickers", nargs='*', help="수집할 종목 티커 리스트")
    parser.add_argument("--all", action="store_true", help="DB 전 종목 업데이트")
    parser.add_argument("--repair", action="store_true", help="[복구모드] 누락 확인을 위해 전체 기간 재수집")
    parser.add_argument("--db", default="db", help="DB 이름")
    
    args = parser.parse_args()
    target_tickers = args.tickers
    
    # 2. --all 옵션 처리
    if args.all:
        try:
            from AI.libs.database.ticker_loader import load_all_tickers_from_db
            print(">> DB에서 전체 종목 리스트를 조회합니다...")
            target_tickers = load_all_tickers_from_db(verbose=True)
        except Exception as e:
            print(f"[Error] 종목 로드 실패: {e}")
            sys.exit(1)

    # 3. 인자가 없을 때: 사용법 안내 및 입력 대기
    if not target_tickers and not args.all:
        print("\n========================================================")
        print(" [Manual Mode] 주식 데이터 수집기")
        print("========================================================")
        print(" 사용 예시:")
        print("  1) 특정 종목 수집  : python market_data.py AAPL TSLA")
        print("  2) 전체 종목 업데이트: python market_data.py --all")
        print("  3) 누락 데이터 복구 : python market_data.py --all --repair")
        print("========================================================")
        print(">> 수집할 종목 코드를 공백으로 구분하여 입력하세요.")
        print("   (종료하려면 그냥 엔터를 누르세요)")
        
        try:
            input_str = sys.stdin.readline().strip()
            if input_str:
                target_tickers = input_str.split()
        except KeyboardInterrupt:
            print("\n취소되었습니다.")
            sys.exit(0)

    # 4. 수집 실행
    if target_tickers:
        # update_market_data 함수에 repair_mode 전달
        update_market_data(target_tickers, db_name=args.db, repair_mode=args.repair)
        print("\n[완료] 모든 작업이 끝났습니다.")
    else:
        print(">> 입력된 종목이 없어 종료합니다.")