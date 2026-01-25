#AI/modules/data_collector/event_data.py
import sys
import os
import time
import requests
import pandas as pd
import yfinance as yf
from datetime import datetime, timedelta, date
from typing import List
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

# FMP API 키 (환경변수 설정 권장)
FMP_API_KEY = os.getenv("FMP_API_KEY", "your_fmp_api_key_here")

class EventDataCollector:
    """
    [이벤트 일정 수집기 - Smart API Mode]
    1. Macro: DB를 먼저 조회하여 미래 데이터가 있으면 API 호출을 생략(Skip)하여 쿼터를 절약합니다.
    2. Earnings: 종목별 실적 발표일은 변동이 잦으므로 매일 체크합니다.
    """

    def __init__(self, db_name: str = "db"):
        self.db_name = db_name
        self.base_url = "https://financialmodelingprep.com/api/v3/economic_calendar"

    def _has_sufficient_macro_data(self) -> bool:
        """
        DB에 향후 일정 데이터가 충분히 존재하는지 확인합니다.
        (API 호출 횟수 절약을 위한 방어 로직)
        """
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            # 오늘 이후에 예정된 MACRO 이벤트(FOMC, CPI 등)가 있는지 카운트
            query = """
                SELECT COUNT(*) 
                FROM public.event_calendar 
                WHERE ticker = 'MACRO' AND event_date > CURRENT_DATE
            """
            cursor.execute(query)
            count = cursor.fetchone()[0]
            
            # 미래 데이터가 5건 이상이면 이미 수집된 것으로 간주 (보통 연간 일정은 10개 이상임)
            if count >= 5:
                print(f"[Event] DB에 이미 {count}건의 미래 거시경제 일정이 존재합니다. (API 호출 Skip)")
                return True
            return False
        except Exception as e:
            print(f"   [Warning] DB 데이터 확인 중 오류: {e}")
            return False
        finally:
            cursor.close()
            conn.close()

    def fetch_macro_from_api(self):
        """FMP API를 호출하여 경제 캘린더 데이터를 가져옵니다."""
        print("[Event] FMP API로 경제 일정 신규 요청 중...")
        
        # 조회 기간: 오늘부터 1년 뒤까지
        today = datetime.now().strftime("%Y-%m-%d")
        next_year = (datetime.now() + timedelta(days=365)).strftime("%Y-%m-%d")
        
        url = f"{self.base_url}?from={today}&to={next_year}&apikey={FMP_API_KEY}"
        
        try:
            response = requests.get(url, timeout=10)
            if response.status_code != 200:
                print(f"   [Error] API 호출 실패: {response.status_code} - {response.text}")
                return []
            
            data = response.json()
            return data
        except Exception as e:
            print(f"   [Error] API 연동 중 예외 발생: {e}")
            return []

    def update_macro_events(self, force_update: bool = False):
        """
        거시경제 일정을 DB에 저장합니다.
        :param force_update: True일 경우 DB 확인 로직을 무시하고 강제로 API를 호출합니다.
        """
        # 1. 데이터 존재 여부 확인 (강제 업데이트가 아닐 경우)
        if not force_update and self._has_sufficient_macro_data():
            return

        # 2. API 호출
        if "your_fmp_api_key_here" in FMP_API_KEY and not os.getenv("FMP_API_KEY"):
             print("[Error] FMP_API_KEY가 설정되지 않아 매크로 데이터 수집을 건너뜁니다.")
             return

        events = self.fetch_macro_from_api()
        if not events:
            return

        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()

        insert_query = """
            INSERT INTO public.event_calendar (event_date, event_type, ticker, description)
            VALUES %s
            ON CONFLICT (event_date, event_type, ticker) 
            DO UPDATE SET description = EXCLUDED.description;
        """

        # 주요 키워드 필터링
        target_keywords = {
            'FOMC': ['FOMC', 'Fed Interest Rate Decision'],
            'CPI': ['CPI', 'Consumer Price Index'],
            'GDP': ['GDP Growth Rate', 'Gross Domestic Product'],
            'PCE': ['PCE', 'Personal Consumption Expenditures'] # PCE 추가
        }

        data_to_insert = []
        seen_events = set()

        for item in events:
            event_name = item.get('event', '')
            event_date_str = item.get('date', '')[:10]
            country = item.get('country', '')

            # 미국 데이터만
            if country != 'US':
                continue

            detected_type = None
            for key, keywords in target_keywords.items():
                if any(k in event_name for k in keywords):
                    detected_type = key
                    break
            
            if detected_type:
                unique_key = (event_date_str, detected_type)
                if unique_key not in seen_events:
                    data_to_insert.append((event_date_str, detected_type, 'MACRO', event_name))
                    seen_events.add(unique_key)

        try:
            if data_to_insert:
                execute_values(cursor, insert_query, data_to_insert)
                conn.commit()
                print(f"   >> Macro 일정 {len(data_to_insert)}건 API 수집 및 저장 완료.")
            else:
                print("   >> 조건에 맞는 주요 이벤트가 없습니다.")
        except Exception as e:
            conn.rollback()
            print(f"   [Error] DB 저장 실패: {e}")
        finally:
            cursor.close()
            conn.close()

    def update_earnings_dates(self, tickers: List[str]):
        """(기존 동일) 기업 실적 발표일 수집"""
        if not tickers: return
        
        # 너무 잦은 로그 출력을 방지하기 위해 print 최소화
        # print(f"[Event] 기업 {len(tickers)}개 실적 발표일 체크...")
        
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        
        insert_query = """
            INSERT INTO public.event_calendar (event_date, event_type, ticker, description)
            VALUES %s
            ON CONFLICT (event_date, event_type, ticker) DO NOTHING;
        """
        
        data_buffer = []
        count = 0
        
        for i, ticker in enumerate(tickers):
            try:
                yf_ticker = yf.Ticker(ticker)
                cal = yf_ticker.calendar
                
                earnings_date = None
                if cal is not None:
                    if isinstance(cal, dict) and 'Earnings Date' in cal:
                        dates = cal['Earnings Date']
                        if dates: earnings_date = dates[0]
                    elif isinstance(cal, pd.DataFrame) and not cal.empty:
                        # 다양한 DataFrame 구조 대응
                        if 'Earnings Date' in cal.index:
                             earnings_date = cal.loc['Earnings Date'].iloc[0]
                        elif 0 in cal.columns:
                             earnings_date = cal.iloc[0, 0]

                if earnings_date:
                    if hasattr(earnings_date, 'date'): earnings_date = earnings_date.date()
                    
                    # 과거 날짜가 아닌 미래 날짜만 저장
                    if earnings_date >= date.today():
                        data_buffer.append((earnings_date, 'EARNINGS', ticker, f"{ticker} Earnings"))
                        count += 1
                
                time.sleep(0.1) # API Rate Limit 고려
                
                if len(data_buffer) >= 50:
                    execute_values(cursor, insert_query, data_buffer)
                    conn.commit()
                    data_buffer = []
            except Exception: 
                continue

        if data_buffer:
            execute_values(cursor, insert_query, data_buffer)
            conn.commit()
            
        cursor.close()
        conn.close()
        if count > 0:
            print(f"[Event] {count}개의 기업 실적 일정 업데이트 완료.")

    def run(self, tickers: List[str] = None):
        # 1. Macro (DB 확인 후 필요시에만 API 호출)
        self.update_macro_events(force_update=False)
        
        # 2. Earnings (항상 최신화)
        if tickers:
            self.update_earnings_dates(tickers)

if __name__ == "__main__":
    import argparse
    from AI.libs.database.ticker_loader import load_all_tickers_from_db

    parser = argparse.ArgumentParser()
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--force", action="store_true", help="DB 확인 없이 API 강제 호출")
    parser.add_argument("--db", default="db")
    args = parser.parse_args()

    # API Key Warning
    if "your_fmp_api_key_here" in FMP_API_KEY and not os.getenv("FMP_API_KEY"):
        print("Warning: FMP_API_KEY not set.")

    collector = EventDataCollector(db_name=args.db)
    
    # 1. Macro Force Update 테스트 가능
    if args.force:
        collector.update_macro_events(force_update=True)
    
    # 2. 전체 실행
    targets = load_all_tickers_from_db() if args.all else None
    collector.run(tickers=targets)