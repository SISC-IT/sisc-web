#AI/modules/data_collector/market_breadth_data.py
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

class MarketBreadthCollector:
    """
    [시장 폭 및 섹터 데이터 수집기]
    - GICS 11개 섹터 + SPY(시장 전체) 수집
    - 매핑되지 않는 섹터의 '보험(Fallback)'으로 SPY를 사용
    """

    def __init__(self, db_name: str = "db"):
        self.db_name = db_name
        
        # [핵심] 섹터 매핑 정의 (표준화)
        # Key: DB에 저장될 표준 섹터명
        # Value: 대표 ETF 티커
        self.SECTOR_ETF_MAP = {
            # 1. GICS 11개 표준 섹터
            'Technology': 'XLK',
            'Financial Services': 'XLF',
            'Healthcare': 'XLV',
            'Consumer Cyclical': 'XLY',
            'Consumer Defensive': 'XLP',
            'Energy': 'XLE',
            'Basic Materials': 'XLB',
            'Industrials': 'XLI',
            'Utilities': 'XLU',
            'Real Estate': 'XLRE',
            'Communication Services': 'XLC',
            
            # 2. [추가] 매핑 실패 시 사용할 '시장 전체' (Fallback)
            'Market': 'SPY' 
        }

        # [보완] yfinance의 변칙적인 섹터명을 표준명으로 연결하는 사전
        # (나중에 전처리나 stock_info 수집 시 활용 가능하지만, 
        #  여기서는 '어떤 ETF 데이터를 어디에 매핑할지'가 중요하므로 참고용 주석)
        # 예: 'Information Technology' -> 'Technology'
        #     'Materials' -> 'Basic Materials'

    def fetch_and_save_sector_returns(self, days_back: int = 365):
        # ... (이전 코드와 로직 동일) ...
        # self.SECTOR_ETF_MAP에 'Market': 'SPY'가 추가되었으므로 
        # 자동으로 SPY 데이터도 'Market'이라는 섹터명으로 저장됩니다.
        
        print("[Breadth] 섹터 ETF(+SPY) 데이터 수집 시작...")
        
        start_date = (datetime.now() - timedelta(days=days_back + 10)).strftime('%Y-%m-%d')
        tickers = list(self.SECTOR_ETF_MAP.values())
        ticker_to_sector = {v: k for k, v in self.SECTOR_ETF_MAP.items()}

        try:
            # 1. 데이터 다운로드
            data = yf.download(tickers, start=start_date, progress=False, auto_adjust=True, threads=True)
            
            if data.empty:
                print("   >> 수집된 데이터가 없습니다.")
                return

            # MultiIndex 처리 (yfinance 버전에 따른 안전장치)
            if isinstance(data.columns, pd.MultiIndex):
                # 'Close' 레벨이 있으면 가져오고, 없으면 전체가 Close라고 가정 시도
                try:
                    closes = data.xs('Close', axis=1, level=0)
                except KeyError:
                    # columns 구조가 (Ticker, PriceType)이 아니라 (PriceType, Ticker) 일수도 있음
                    # yfinance 최근 버전은 (Price, Ticker) 구조임.
                    # 여기서는 간단히 'Adj Close'나 'Close'를 찾아서 처리
                    if 'Close' in data.columns.get_level_values(0):
                         closes = data.xs('Close', axis=1, level=0)
                    else:
                         # 구조를 알 수 없을 때
                         print("   [Warning] 데이터 컬럼 구조 인식 불가. Skip.")
                         return
            else:
                closes = data['Close']

            # 2. 수익률 계산
            returns = closes.pct_change()

            # 3. DB 저장
            conn = get_db_conn(self.db_name)
            cursor = conn.cursor()

            insert_query = """
                INSERT INTO public.sector_returns (date, sector, etf_ticker, return, close)
                VALUES %s
                ON CONFLICT (date, sector) 
                DO UPDATE SET 
                    return = EXCLUDED.return,
                    close = EXCLUDED.close,
                    etf_ticker = EXCLUDED.etf_ticker;
            """

            data_to_insert = []
            
            for date_idx, row in returns.iterrows():
                date_val = date_idx.date()
                
                for etf_ticker in tickers:
                    if etf_ticker not in row: continue
                    ret_val = row[etf_ticker]
                    if pd.isna(ret_val): continue
                    
                    # 종가 (closes 데이터프레임에서 가져옴)
                    if etf_ticker in closes.columns:
                        close_val = closes.loc[date_idx, etf_ticker]
                    else:
                        close_val = 0.0

                    sector_name = ticker_to_sector.get(etf_ticker, 'Unknown')

                    data_to_insert.append((
                        date_val,
                        sector_name,
                        etf_ticker,
                        float(ret_val),
                        float(close_val)
                    ))

            if data_to_insert:
                batch_size = 1000
                for i in range(0, len(data_to_insert), batch_size):
                    execute_values(cursor, insert_query, data_to_insert[i:i+batch_size])
                conn.commit()
                print(f"   >> 섹터(+Market) 수익률 {len(data_to_insert)}건 저장 완료.")
            
        except Exception as e:
            conn.rollback()
            print(f"   [Error] 섹터 데이터 처리 중 오류: {e}")
        finally:
            if 'cursor' in locals(): cursor.close()
            if 'conn' in locals(): conn.close()

    def run(self, repair_mode: bool = False):
        # Repair 모드면 2010년부터, 아니면 최근 2년치
        days = 365 * 15 if repair_mode else 365 * 2
        self.fetch_and_save_sector_returns(days_back=days)

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--repair", action="store_true")
    parser.add_argument("--db", default="db")
    args = parser.parse_args()

    collector = MarketBreadthCollector(db_name=args.db)
    collector.run(repair_mode=args.repair)