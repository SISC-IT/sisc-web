# AI/modules/finder/selector.py
"""
[종목 선정 모듈]
- DB에서 분석 대상 종목(Ticker) 리스트를 조회하고 필터링합니다.
- 거래량, 데이터 충실도 등을 기준으로 유효한 종목만 선별합니다.
"""

import sys
import os
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
    """
    conn = None
    cursor = None
    
    try:
        conn = get_db_conn()
        cursor = conn.cursor()
        
        # 최근 날짜 기준으로 거래대금(종가 * 거래량) 상위 종목 조회
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
            print("[Finder] DB에서 조회된 종목이 없습니다.")
            return ["AAPL", "TSLA", "MSFT", "GOOGL", "NVDA"]
            
        return tickers
        
    except Exception as e:
        print(f"[Finder][Error] 종목 조회 중 DB 오류: {e}")
        # DB 연결 실패 등의 경우 기본 티커 반환
        return ["AAPL", "TSLA", "MSFT", "GOOGL", "NVDA"]
        
    finally:
        if cursor: cursor.close()
        if conn: conn.close()

def load_all_tickers_from_db(verbose: bool = True) -> List[str]:
    """
    DB에 존재하는 모든 종목 코드를 가져옵니다.
    """
    conn = None
    cursor = None
    try:
        conn = get_db_conn()
        cursor = conn.cursor()
        
        # DB에 있는 모든 고유 티커 조회 (LIMIT 없음)
        query = "SELECT DISTINCT ticker FROM public.price_data ORDER BY ticker"
        
        cursor.execute(query)
        rows = cursor.fetchall()
        
        tickers = [row[0] for row in rows]
        
        if verbose:
            print(f"[Finder] DB 전체 종목 수: {len(tickers)}개")
            
        if not tickers:
            if verbose: print("[Finder] DB가 비어있어 기본값(Big Tech)을 반환합니다.")
            return ["AAPL", "TSLA", "MSFT", "GOOGL", "NVDA"]
            
        return tickers
        
    except Exception as e:
        print(f"[Finder][Error] 전체 티커 로드 실패: {e}")
        # 실패 시 기본값 반환
        return ["AAPL", "TSLA", "MSFT", "GOOGL", "NVDA"]
        
    finally:
        if cursor: cursor.close()
        if conn: conn.close()