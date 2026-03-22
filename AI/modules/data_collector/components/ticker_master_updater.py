#AI/modules/data_collector/ticker_master_updater.py
import sys
import os
import json
import pandas as pd
from typing import List, Dict
import requests
from datetime import datetime
from io import StringIO

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

class TickerMasterUpdater:
    """
    [종목 마스터 관리자]
    - 데이터 파이프라인의 최우선 실행 모듈 (Step 0)
    - 외부 소스(Wikipedia, ETF holdings) 또는 내부 파일에서 티커 리스트를 확보하여
      DB의 'stock_info' 및 'company_names' 테이블을 초기화합니다.
    - 다른 수집기들이 참조할 '대상 종목군'을 정의하는 역할을 합니다.
    """

    def __init__(self, db_name: str = "db"):
        self.db_name = db_name

    def fetch_sp500_tickers(self) -> List[Dict]:
        """
        Wikipedia에서 최신 S&P 500 종목 리스트와 기업명을 크롤링합니다.
        """
        print("[Master] S&P 500 리스트 다운로드 중 (Wikipedia)...")
        try:
            url = 'https://en.wikipedia.org/wiki/List_of_S%26P_500_companies'
            # 봇 차단 우회를 위한 User-Agent 헤더 추가
            headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
            
            response = requests.get(url, headers=headers)
            response.raise_for_status()
            
            # StringIO로 텍스트를 감싸서 pandas로 읽기
            tables = pd.read_html(StringIO(response.text))
            df = tables[0]
            
            # yfinance 호환성을 위해 티커 변경 (예: BRK.B -> BRK-B)
            df['Symbol'] = df['Symbol'].str.replace('.', '-', regex=False)
            
            tickers = []
            for _, row in df.iterrows():
                tickers.append({
                    "ticker": row['Symbol'],
                    "name": row['Security'],
                    "sector": row['GICS Sector']
                })
            return tickers
        except Exception as e:
            print(f"[Error] S&P 500 로드 실패: {e}")
            return []

    def fetch_nasdaq100_tickers(self) -> List[Dict]:
        """
        Wikipedia에서 NASDAQ 100 종목 리스트를 가져옵니다.
        """
        print("[Master] NASDAQ 100 리스트 다운로드 중...")
        try:
            url = 'https://en.wikipedia.org/wiki/Nasdaq-100'
            # 봇 차단 우회를 위한 User-Agent 헤더 추가
            headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
            
            response = requests.get(url, headers=headers)
            response.raise_for_status()
            
            tables = pd.read_html(StringIO(response.text))
            
            # 보통 5번째 테이블이 구성 종목 (Wikipedia 구조 변경 시 확인 필요)
            # 안전하게 컬럼명으로 찾기
            df = None
            for table in tables:
                if 'Ticker' in table.columns and 'Company' in table.columns:
                    df = table
                    break
            
            if df is None:
                return []

            df['Ticker'] = df['Ticker'].str.replace('.', '-', regex=False)
            
            tickers = []
            for _, row in df.iterrows():
                tickers.append({
                    "ticker": row['Ticker'],
                    "name": row['Company'],
                    "sector": row.get('GICS Sector', None) # 없을 수도 있음
                })
            return tickers
        except Exception as e:
            print(f"[Error] NASDAQ 100 로드 실패: {e}")
            return []

    def save_to_db(self, ticker_list: List[Dict]):
        if not ticker_list:
            print("[Master] 저장할 종목이 없습니다.")
            return
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        
        print(f"[Master] DB 업데이트 시작 ({len(ticker_list)}개 종목) -> DB: {self.db_name}")
        
        try:
            # 1. stock_info 테이블 업데이트 (전체 배치 처리)
            query_stock_info = """
                INSERT INTO public.stock_info (ticker)
                VALUES (%s)
                ON CONFLICT (ticker) DO NOTHING;
            """
            stock_info_data = [(item['ticker'],) for item in ticker_list]
            cursor.executemany(query_stock_info, stock_info_data)
            conn.commit() # 여기서 중간 저장!
            print(f"[Master] stock_info 테이블 동기화 완료.")

            # 2. company_names 테이블 업데이트
            query_company_names = """
                INSERT INTO public.company_names (ticker, company_name)
                VALUES (%s, %s)
                ON CONFLICT (ticker) DO UPDATE SET company_name = EXCLUDED.company_name;
            """
            
            success_count = 0
            for item in ticker_list:
                if not item.get('name'): continue
                try:
                    cursor.execute(query_company_names, (item['ticker'], item['name']))
                    success_count += 1
                except Exception as e:
                    conn.rollback() # 이 건만 취소
                    print(f"  - [{item['ticker']}] 업데이트 건너뜀")
                    continue
            
            conn.commit() # 최종 저장!
            print(f"[Master] company_names 테이블 {success_count}개 동기화 완료.")
            
        except Exception as e:
            conn.rollback()
            print(f"[Master][Error] 전체 프로세스 중 오류 발생: {e}")
        finally:
            cursor.close()
            conn.close()

# ----------------------------------------------------------------------
# [실행 모드]
# ----------------------------------------------------------------------
if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="[시스템 초기화] 종목 마스터 DB 업데이트")
    parser.add_argument("--source", type=str, default="sp500", 
                        choices=["sp500", "nasdaq", "file", "all"],
                        help="종목 리스트 원천 (sp500, nasdaq, file, all)")
    parser.add_argument("--file", type=str, default="AI/weekly_tickers.json",
                        help="source가 file일 때 읽어올 JSON 경로")
    parser.add_argument("--db", default="db", help="DB 이름")
    
    args = parser.parse_args()
    
    updater = TickerMasterUpdater(db_name=args.db)
    final_list = []
    
    # 1. S&P 500
    if args.source in ["sp500", "all"]:
        sp500 = updater.fetch_sp500_tickers()
        print(f">> S&P 500: {len(sp500)}개 발견")
        final_list.extend(sp500)
        
    # 2. NASDAQ 100
    if args.source in ["nasdaq", "all"]:
        ndx = updater.fetch_nasdaq100_tickers()
        print(f">> NASDAQ 100: {len(ndx)}개 발견")
        final_list.extend(ndx)
    
    # 중복 제거 (티커 기준)
    unique_tickers = {item['ticker']: item for item in final_list}.values()
    
    print(f"Total Unique Tickers: {len(unique_tickers)}")
    
    if unique_tickers:
        updater.save_to_db(list(unique_tickers))
        print("\n✅ 종목 마스터 업데이트 완료.")
        print("💡 다음 단계: 'python stock_info_collector.py --all'을 실행하여 섹터 정보를 채우세요.")
    else:
        print("❌ 업데이트할 종목이 없습니다.")