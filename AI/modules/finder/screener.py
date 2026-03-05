#AI/modules/finder/screener.py

import os
import sys
import json
import pandas as pd

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

class DynamicScreener:
    """
    [다이나믹 스크리너]
    - 특정 날짜를 기준으로 시장의 주도주를 필터링하여 워치리스트에 자동 등록합니다.
    """
    def __init__(self, db_name="db"):
        self.db_name = db_name
        self.config_path = os.path.join(project_root, "AI", "config", "watchlist.json")

    def update_watchlist(self, target_date: str, top_n: int = 30) -> list:
        """
        [Rule] 
        1. 시가총액 100억 달러 (약 13조 원) 이상의 대형 우량주 (Large-Cap)
        2. 최근 10일 평균 거래대금 상위 N개 추출
        """
        print(f"🔍 [Screener] {target_date} 기준 다이나믹 스크리닝 시작 (우량주 Top {top_n})...")
        conn = get_db_conn(self.db_name)
        if not conn:
            return []

        # Look-ahead bias 방지 및 우량주 필터링 쿼리
        query = f"""
            SELECT p.ticker, AVG(p.close * p.volume) as dollar_vol
            FROM public.price_data p
            JOIN public.stock_info s ON p.ticker = s.ticker
            WHERE p.date <= '{target_date}' 
              AND p.date >= '{target_date}'::date - INTERVAL '10 days'
              AND s.market_cap >= 10000000000  --  [안전장치 1] 시총 100억 달러 이상 (Large-Cap)
            GROUP BY p.ticker
            HAVING AVG(p.close * p.volume) > 0
            ORDER BY dollar_vol DESC
            LIMIT {top_n};
        """

        try:
            df = pd.read_sql(query, conn)
            if df.empty:
                print("⚠️ [Screener] 조건을 만족하는 종목이 없습니다.")
                return []
            
            tickers = df['ticker'].tolist()
            
            # config 폴더가 없으면 생성
            os.makedirs(os.path.dirname(self.config_path), exist_ok=True)
            
            # JSON 파일 덮어쓰기
            with open(self.config_path, "w", encoding="utf-8") as f:
                json.dump({"tickers": tickers}, f, indent=4)
                
            print(f"✅ [Screener] 워치리스트 갱신 완료: {len(tickers)}종목")
            return tickers
            
        except Exception as e:
            print(f"❌ [Screener] 스크리닝 중 오류: {e}")
            return []
        finally:
            conn.close()