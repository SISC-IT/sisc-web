import json
import os
import sys

import pandas as pd

current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.config import TradingConfig, load_trading_config
from AI.libs.database.connection import get_db_conn


class DynamicScreener:
    """
    [다이나믹 스크리너]
    - 특정 날짜를 기준으로 시장의 주도주를 필터링하여 워치리스트에 자동 등록합니다.
    """

    def __init__(self, db_name: str | None = None, config: TradingConfig | None = None):
        self.config = config or load_trading_config()
        self.db_name = db_name or self.config.pipeline.db_name
        self.watchlist_path = self.config.screener.watchlist_path

    def update_watchlist(self, target_date: str, top_n: int | None = None) -> list[str]:
        """
        [Rule]
        1. 설정된 최소 시가총액 이상의 대형 우량주만 대상으로 삼습니다.
        2. 최근 lookback_days 평균 거래대금 기준 상위 N개를 추출합니다.
        """
        screener_config = self.config.screener
        limit = int(top_n if top_n is not None else screener_config.top_n)
        lookback_days = int(screener_config.lookback_days)
        min_market_cap = float(screener_config.min_market_cap)

        print(f"[Screener] Building watchlist for {target_date} (top {limit})...")
        conn = get_db_conn(self.db_name)
        if not conn:
            return []

        # Look-ahead bias 방지 및 우량주 필터링 쿼리
        query = f"""
            SELECT p.ticker, AVG(p.close * p.volume) AS dollar_vol
            FROM public.price_data p
            JOIN public.stock_info s ON p.ticker = s.ticker
            WHERE p.date <= '{target_date}'
              AND p.date >= '{target_date}'::date - INTERVAL '{lookback_days} days'
              AND s.market_cap >= {min_market_cap}
            GROUP BY p.ticker
            HAVING AVG(p.close * p.volume) > 0
            ORDER BY dollar_vol DESC
            LIMIT {limit};
        """

        try:
            df = pd.read_sql(query, conn)
            if df.empty:
                print("[Screener] No tickers matched the configured rules.")
                return []

            tickers = df["ticker"].tolist()
            # watchlist 출력 경로가 없으면 생성
            os.makedirs(os.path.dirname(self.watchlist_path), exist_ok=True)

            with open(self.watchlist_path, "w", encoding="utf-8") as handle:
                json.dump({"tickers": tickers}, handle, indent=4)

            print(f"[Screener] Watchlist updated: {len(tickers)} tickers")
            return tickers
        except Exception as e:
            print(f"[Screener] Failed to update watchlist: {e}")
            return []
        finally:
            conn.close()
