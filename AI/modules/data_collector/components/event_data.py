import sys
import os
import time
import requests
import pandas as pd
import numpy as np
import yfinance as yf
from datetime import datetime, timedelta, date
from typing import List, Optional
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

# FMP API 키
FMP_API_KEY = os.getenv("FMP_API_KEY", "your_fmp_api_key_here")

class EventDataCollector:
    """
    [이벤트 일정 및 서프라이즈 수집기]
    - Macro: FMP API를 통해 Forecast/Actual 수집
    - Earnings: yfinance를 통해 EPS Estimate/Reported 수집
    - Schema: event_date, event_type, ticker, description, forecast, actual
    """

    def __init__(self, db_name: str = "db"):
        self.db_name = db_name
        self.base_url = "https://financialmodelingprep.com/api/v3/economic_calendar"

    def _has_sufficient_macro_data(self) -> bool:
        """DB에 미래 데이터가 충분한지 확인"""
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            query = """
                SELECT COUNT(*) 
                FROM public.event_calendar 
                WHERE ticker = 'MACRO' AND event_date > CURRENT_DATE
            """
            cursor.execute(query)
            count = cursor.fetchone()[0]
            # 미래 데이터가 5개 이상이면 Skip (단, Actual 업데이트를 위해 force_update 필요할 수 있음)
            return count >= 5
        except Exception:
            return False
        finally:
            cursor.close()
            conn.close()

    def fetch_macro_from_api(self):
        """FMP API 호출"""
        print("[Event] FMP API로 경제 지표(Surprise) 수집 중...")
        # 과거 데이터 업데이트(Actual 채우기)를 위해 시작일을 30일 전으로 설정
        start_date = (datetime.now() - timedelta(days=30)).strftime("%Y-%m-%d")
        end_date = (datetime.now() + timedelta(days=365)).strftime("%Y-%m-%d")
        
        url = f"{self.base_url}?from={start_date}&to={end_date}&apikey={FMP_API_KEY}"
        try:
            response = requests.get(url, timeout=10)
            if response.status_code != 200:
                print(f"   [Error] API 호출 실패: {response.status_code}")
                return []
            return response.json()
        except Exception as e:
            print(f"   [Error] API 연동 오류: {e}")
            return []

    def update_macro_events(self, force_update: bool = False):
        """거시경제 일정 및 수치 저장"""
        # force_update가 False여도, 지난달의 Actual 값이 비어있을 수 있으므로 
        # API 호출을 아예 막기보다, API 호출 부담이 적다면 주기적으로 실행하는 것이 좋습니다.
        # 여기서는 '미래 일정'만 체크하는 로직 유지하되, 필요시 force_update=True로 실행 권장.
        if not force_update and self._has_sufficient_macro_data():
            # print("   >> Macro 일정 충분함 (Skip). Actual 업데이트 필요시 --repair 사용.")
            return

        if "your_fmp_api_key_here" in FMP_API_KEY and not os.getenv("FMP_API_KEY"):
             print("[Error] FMP_API_KEY 미설정.")
             return

        events = self.fetch_macro_from_api()
        if not events: return

        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()

        # [SQL 수정] actual, forecast 추가
        insert_query = """
            INSERT INTO public.event_calendar 
            (event_date, event_type, ticker, description, forecast, actual)
            VALUES %s
            ON CONFLICT (event_date, event_type, ticker) 
            DO UPDATE SET 
                description = EXCLUDED.description,
                forecast = EXCLUDED.forecast,
                actual = EXCLUDED.actual;
        """

        target_keywords = {
            'FOMC': ['FOMC', 'Fed Interest Rate Decision'],
            'CPI': ['CPI', 'Consumer Price Index'],
            'GDP': ['GDP Growth Rate', 'Gross Domestic Product'],
            'PCE': ['PCE', 'Personal Consumption Expenditures']
        }

        data_to_insert = []
        seen = set()

        for item in events:
            if item.get('country') != 'US': continue
            
            evt_date = item.get('date', '')[:10]
            evt_name = item.get('event', '')
            
            # 수치 파싱 (None 처리)
            estimate_val = item.get('estimate')
            actual_val = item.get('actual')
            
            detected_type = None
            for key, keywords in target_keywords.items():
                if any(k in evt_name for k in keywords):
                    detected_type = key
                    break
            
            if detected_type:
                # 키: 날짜+타입
                if (evt_date, detected_type) not in seen:
                    data_to_insert.append((
                        evt_date, 
                        detected_type, 
                        'MACRO', 
                        evt_name,
                        estimate_val, # forecast
                        actual_val    # actual
                    ))
                    seen.add((evt_date, detected_type))

        try:
            if data_to_insert:
                execute_values(cursor, insert_query, data_to_insert)
                conn.commit()
                print(f"   >> Macro 일정 및 서프라이즈 {len(data_to_insert)}건 저장 완료.")
            else:
                print("   >> 저장할 주요 이벤트가 없습니다.")
        except Exception as e:
            conn.rollback()
            print(f"   [Error] DB 저장 실패: {e}")
        finally:
            cursor.close()
            conn.close()

    def update_earnings_dates(self, tickers: List[str]):
        """
        기업 실적 발표일 및 EPS 예측치/실제치 저장
        yfinance의 get_earnings_dates() 사용
        """
        if not tickers: return
        print(f"[Event] 기업 {len(tickers)}개 실적 서프라이즈 데이터 수집 중...")
        
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        
        insert_query = """
            INSERT INTO public.event_calendar 
            (event_date, event_type, ticker, description, forecast, actual)
            VALUES %s
            ON CONFLICT (event_date, event_type, ticker) 
            DO UPDATE SET
                forecast = EXCLUDED.forecast,
                actual = EXCLUDED.actual;
        """
        
        data_buffer = []
        
        for i, ticker in enumerate(tickers):
            try:
                yf_ticker = yf.Ticker(ticker)
                
                # [변경] calendar 대신 get_earnings_dates 사용 (과거+미래 데이터 포함)
                # limit=12 (최근 1년 ~ 미래 1년 정도)
                df_earnings = yf_ticker.get_earnings_dates(limit=8)
                
                if df_earnings is None or df_earnings.empty:
                    continue

                # 인덱스가 날짜임. 컬럼: 'EPS Estimate', 'Reported EPS', 'Surprise(%)'
                # 미래 데이터는 Reported EPS가 NaN임.
                
                for dt_idx, row in df_earnings.iterrows():
                    evt_date = dt_idx.date()
                    
                    # 너무 먼 과거 데이터는 스킵 (최근 6개월 ~ 미래 1년만 저장)
                    if evt_date < (date.today() - timedelta(days=180)):
                        continue
                    if evt_date > (date.today() + timedelta(days=365)):
                        continue

                    estimate = row.get('EPS Estimate')
                    reported = row.get('Reported EPS')
                    
                    # NaN -> None 변환 (DB 저장을 위해)
                    if pd.isna(estimate): estimate = None
                    else: estimate = float(estimate)
                    
                    if pd.isna(reported): reported = None
                    else: reported = float(reported)
                    
                    description = f"{ticker} Earnings Release"
                    
                    data_buffer.append((
                        evt_date,
                        'EARNINGS',
                        ticker,
                        description,
                        estimate, # forecast
                        reported  # actual
                    ))

                # API 호출 속도 조절
                time.sleep(0.1)
                
                if len(data_buffer) >= 50:
                    execute_values(cursor, insert_query, data_buffer)
                    conn.commit()
                    data_buffer = []

            except Exception:
                # yfinance 에러 발생 시 조용히 넘어감 (데이터 없는 경우 다수)
                continue

        if data_buffer:
            execute_values(cursor, insert_query, data_buffer)
            conn.commit()
            
        cursor.close()
        conn.close()
        print(f"   >> 실적 데이터 업데이트 완료.")

    def run(self, tickers: List[str] = None):
        # 기본적으로 Macro는 업데이트
        self.update_macro_events(force_update=False)
        if tickers:
            self.update_earnings_dates(tickers)

if __name__ == "__main__":
    import argparse
    from AI.libs.database.ticker_loader import load_all_tickers_from_db

    parser = argparse.ArgumentParser()
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--db", default="db")
    args = parser.parse_args()

    collector = EventDataCollector(db_name=args.db)
    
    if args.force:
        collector.update_macro_events(force_update=True)
    
    targets = load_all_tickers_from_db() if args.all else None
    collector.run(tickers=targets)