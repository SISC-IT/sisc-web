# AI/modules/finder/selector.py
"""
[종목 선정 모듈]
- DB에서 분석 대상 종목(Ticker) 리스트를 조회하고 필터링합니다.
- 거래량, 데이터 충실도 등을 기준으로 유효한 종목만 선별합니다.
"""

import sys
import os
import pandas as pd
from typing import List

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

def get_target_tickers(limit: int = 50, min_price: float = 1000) -> List[str]:
    """
    분석 대상 종목 리스트를 반환합니다.
    
    Args:
        limit (int): 가져올 최대 종목 수 (거래량 상위 순)
        min_price (float): 최소 주가 필터
        
    Returns:
        List[str]: 종목 코드 리스트 (예: ['AAPL', 'MSFT', ...])
    """
    conn = get_db_conn()
    cursor = conn.cursor()
    
    try:
        # 최근 날짜 기준으로 거래대금(종가 * 거래량) 상위 종목 조회
        # (최근 데이터가 있는 종목만 대상으로 함)
        query = """
            WITH RecentData AS (
                SELECT ticker, close, volume, date,
                       ROW_NUMBER() OVER (PARTITION BY ticker ORDER BY date DESC) as rn
                FROM public.price_data
            )
            SELECT ticker
            FROM RecentData
            WHERE rn = 1
              AND close >= %s
            ORDER BY (close * volume) DESC
            LIMIT %s
        """
        
        cursor.execute(query, (min_price, limit))
        rows = cursor.fetchall()
        
        tickers = [row[0] for row in rows]
        
        if not tickers:
            print("[Finder] 조회된 종목이 없습니다. (DB가 비어있을 수 있음)")
            # DB가 비어있을 경우 테스트용 티커 반환
            return ["AAPL", "TSLA", "MSFT", "GOOGL", "NVDA"]
            
        print(f"[Finder] 상위 {len(tickers)}개 종목 선정 완료.")
        return tickers
        
    except Exception as e:
        print(f"[Finder][Error] 종목 조회 실패: {e}")
        return ["AAPL"] # 실패 시 기본값
    finally:
        cursor.close()
        conn.close()

def load_all_tickers_from_db(verbose: bool = True) -> List[str]:
    """
    DB에 존재하는 모든 종목 코드를 가져옵니다. (AutoML 등에서 사용)
    """