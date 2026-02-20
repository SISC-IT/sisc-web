# AI/modules/data_collector/market_breadth_data.py
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
    [시장 폭, 섹터 및 벤치마크 지수 수집기]
    - GICS 섹터 ETF, SPY(시장 전체), 주요 국가 지수(Index)를 수집하여 
    - public.sector_returns 테이블에 통합 저장합니다.
    - 거래량(Volume) 없이 종가와 수익률만 저장하므로 노이즈가 없습니다.
    """

    def __init__(self, db_name: str = "db"):
        self.db_name = db_name
        
        # [핵심] 섹터 및 벤치마크 매핑 정의
        # Key: DB에 저장될 표준 명칭 (sector 컬럼)
        # Value: 야후 파이낸스 티커 (etf_ticker 컬럼)
        self.SECTOR_ETF_MAP = {
            # 1. 미국 GICS 11개 표준 섹터 (ETF)
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
            
            # 2. 시장 전체 (Tradable Proxy) fallback 용 지수
            'Market': 'SPY', 

            # 3. [통합] 주요 시장 벤치마크 지수 (Index)
            # 지수는 Volume 데이터가 부정확하므로 sector_returns 테이블(수익률/종가)에 넣는 것이 최적입니다.
            'S&P 500': '^GSPC',
            'NASDAQ': '^IXIC',
            'Dow Jones': '^DJI',
            'Russell 2000': '^RUT',
            'KOSPI': '^KS11',
            'KOSDAQ': '^KQ11',
            'VIX': '^VIX'  # VIX도 여기서 함께 추적
        }

    def fetch_and_save_sector_returns(self, days_back: int = 365):
        print("[Breadth] 섹터 ETF 및 벤치마크 지수 데이터 수집 시작...")
        
        # 수집 안전성을 위해 10일 더 여유있게 조회
        start_date = (datetime.now() - timedelta(days=days_back + 10)).strftime('%Y-%m-%d')
        tickers = list(self.SECTOR_ETF_MAP.values())
        
        # 티커 -> 섹터명 역매핑 (저장 시 사용)
        ticker_to_sector = {v: k for k, v in self.SECTOR_ETF_MAP.items()}

        try:
            # 1. 데이터 다운로드 (일괄 다운로드)
            # auto_adjust=True: 수정 주가 반영
            data = yf.download(tickers, start=start_date, progress=False, auto_adjust=True, threads=True)
            
            if data.empty:
                print("   >> 수집된 데이터가 없습니다.")
                return

            # 2. 종가(Close) 데이터 추출 로직
            # yfinance 버전에 따라 컬럼 구조가 다를 수 있어 방어 코드 적용
            closes = pd.DataFrame()
            
            if isinstance(data.columns, pd.MultiIndex):
                # Case A: (Price, Ticker) 구조 또는 (Ticker, Price) 구조
                # 'Close' 레벨이 존재하는지 확인
                if 'Close' in data.columns.get_level_values(0):
                    closes = data.xs('Close', axis=1, level=0)
                elif 'Close' in data.columns.get_level_values(1):
                    closes = data.xs('Close', axis=1, level=1)
                else:
                    # 'Close'가 없으면 최후의 수단으로 그냥 data 사용
                    print("   [Error] 데이터 프레임 구조에서 Close 컬럼을 찾을 수 없습니다.")
                    return
            else:
                # MultiIndex가 아닌 경우 (단일 티커 요청 시 등)
                if 'Close' in data.columns:
                    closes = data['Close']
                else:
                    closes = data # 전체가 종가라고 가정

            # 3. 수익률 계산 (전일 대비 변동률)
            returns = closes.pct_change()

            # 4. DB 저장
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
            
            # DataFrame 순회하며 데이터 리스트 생성
            for date_idx, row in returns.iterrows():
                date_val = date_idx.date()
                
                for ticker in tickers:
                    # 해당 날짜/티커에 데이터가 없으면 스킵
                    if ticker not in row or pd.isna(row[ticker]):
                        continue
                    
                    ret_val = row[ticker]
                    
                    # 종가 가져오기 (closes DF에서)
                    close_val = 0.0
                    if ticker in closes.columns:
                        val = closes.loc[date_idx, ticker]
                        if pd.notna(val):
                            close_val = float(val)

                    sector_name = ticker_to_sector.get(ticker, 'Unknown')

                    data_to_insert.append((
                        date_val,
                        sector_name,
                        ticker,
                        float(ret_val),
                        float(close_val)
                    ))

            if data_to_insert:
                # 대량 Insert 시 배치 처리
                batch_size = 1000
                for i in range(0, len(data_to_insert), batch_size):
                    execute_values(cursor, insert_query, data_to_insert[i:i+batch_size])
                
                conn.commit()
                print(f"   >> 섹터/SPY/지수 데이터 {len(data_to_insert)}건 저장 완료.")
            else:
                print("   >> 저장할 유효한 데이터가 없습니다.")
            
        except Exception as e:
            conn.rollback()
            print(f"   [Error] 섹터/지수 데이터 처리 중 오류: {e}")
        finally:
            if 'cursor' in locals(): cursor.close()
            if 'conn' in locals(): conn.close()

    def run(self, repair_mode: bool = False):
        """
        실행 진입점
        - repair_mode=True: 2010년부터 전체 재수집
        - repair_mode=False: 최근 2년치만 업데이트
        """
        days = 365 * 16 if repair_mode else 365 * 2
        self.fetch_and_save_sector_returns(days_back=days)

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--repair", action="store_true", help="전체 기간 재수집")
    parser.add_argument("--db", default="db", help="DB 이름")
    args = parser.parse_args()

    collector = MarketBreadthCollector(db_name=args.db)
    collector.run(repair_mode=args.repair)