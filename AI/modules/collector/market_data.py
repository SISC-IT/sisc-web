# AI/modules/collector/market_data.py
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
            # yfinance는 end 날짜를 포함하지 않으므로 하루 더해줌
            # next_day = (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%d")
            
            print(f"   [{ticker}] 다운로드 중 ({start_date} ~ Today)...")
            df = yf.download(ticker, start=start_date, progress=False)
            
            if df.empty:
                print(f"   [{ticker}] 새 데이터 없음.")
                continue
                
            # 4. 데이터 전처리 및 DB 저장
            insert_query = """
                INSERT INTO public.price_data (date, ticker, open, high, low, close, volume, adjusted_close)
                VALUES %s
                ON CONFLICT (date, ticker) DO NOTHING
            """
            
            data_to_insert = []
            for index, row in df.iterrows():
                date_val = index.date()
                open_val = float(row['Open'].iloc[0] if isinstance(row['Open'], pd.Series) else row['Open'])
                high_val = float(row['High'].iloc[0] if isinstance(row['High'], pd.Series) else row['High'])
                low_val = float(row['Low'].iloc[0] if isinstance(row['Low'], pd.Series) else row['Low'])
                close_val = float(row['Close'].iloc[0] if isinstance(row['Close'], pd.Series) else row['Close'])
                vol_val = int(row['Volume'].iloc[0] if isinstance(row['Volume'], pd.Series) else row['Volume'])
                adj_close_val = float(row['Adj Close'].iloc[0] if isinstance(row['Adj Close'], pd.Series) else row['Adj Close'])

                data_to_insert.append((
                    date_val, ticker, open_val, high_val, low_val, close_val, vol_val, adj_close_val
                ))
            
            if data_to_insert:
                execute_values(cursor, insert_query, data_to_insert)
                conn.commit()
                print(f"   [{ticker}] {len(data_to_insert)}건 저장 완료.")
                
    except Exception as e:
        conn.rollback()
        print(f"[Collector][Error] 시장 데이터 업데이트 실패: {e}")
    finally:
        cursor.close()
        conn.close()