# AI/modules/collector/stock_info_collector.py
"""
[종목 정보 수집기]
- yfinance의 Ticker.info를 사용하여 섹터(Sector), 산업(Industry), 시가총액 등을 수집합니다.
- 수집된 정보는 public.stock_info 테이블에 저장됩니다.
- 이 작업은 API 호출 속도가 느리므로, 자주 실행하지 않고 월 1회 또는 필요시 실행을 권장합니다.
"""

import sys
import os
import time
import yfinance as yf
from typing import List
from datetime import datetime

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

def update_stock_info(tickers: List[str], db_name: str = "db"):
    """
    지정된 티커들의 메타 정보(Sector, Industry 등)를 수집하여 DB에 Upsert 합니다.
    """
    print(f"[Info Collector] 총 {len(tickers)}개 종목 정보 업데이트 시작...")
    
    conn = get_db_conn(db_name)
    cursor = conn.cursor()
    
    # Upsert 쿼리 (이미 있으면 업데이트)
    upsert_query = """
        INSERT INTO public.stock_info (ticker, sector, industry, market_cap, updated_at)
        VALUES (%s, %s, %s, %s, %s)
        ON CONFLICT (ticker) 
        DO UPDATE SET
            sector = EXCLUDED.sector,
            industry = EXCLUDED.industry,
            market_cap = EXCLUDED.market_cap,
            updated_at = EXCLUDED.updated_at;
    """
    
    success_count = 0
    fail_count = 0
    
    try:
        for i, ticker in enumerate(tickers):
            try:
                # 진행 상황 출력 (10개 단위)
                if i % 10 == 0:
                    print(f"   >> 진행 중... ({i}/{len(tickers)})")

                # yfinance API 호출 (네트워크 통신 발생)
                yf_ticker = yf.Ticker(ticker)
                info = yf_ticker.info
                
                # 필요한 정보 추출 (없으면 None)
                sector = info.get('sector')
                industry = info.get('industry')
                market_cap = info.get('marketCap')
                
                # 유효성 체크: 최소한 섹터 정보라도 있어야 저장 가치가 있음
                if not sector and not industry:
                    # 정보가 아예 없으면 건너뜀 (ETF나 상장폐지 종목일 수도 있음)
                    # print(f"   [{ticker}] 정보 없음(Skip)")
                    fail_count += 1
                    continue

                # DB 실행
                cursor.execute(upsert_query, (
                    ticker, 
                    sector, 
                    industry, 
                    market_cap, 
                    datetime.now()
                ))
                success_count += 1
                
                # 너무 빠른 요청 방지 (0.2초 대기)
                time.sleep(0.2)
                
            except Exception as e:
                print(f"   [{ticker}] 에러 발생: {e}")
                fail_count += 1
                continue

        conn.commit()
        print(f"\n[Info Collector] 완료! (성공: {success_count}건, 실패/없음: {fail_count}건)")
        
    except Exception as e:
        conn.rollback()
        print(f"[Info Collector][Fatal Error] 작업 중단: {e}")
    finally:
        cursor.close()
        conn.close()

# ----------------------------------------------------------------------
# [실행 모드]
# 사용법: python stock_info_collector.py --all
# ----------------------------------------------------------------------
if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="주식 종목 정보(섹터/산업) 수집기")
    parser.add_argument("tickers", nargs='*', help="수집할 종목 티커 (예: AAPL)")
    parser.add_argument("--all", action="store_true", help="DB에 있는 모든 종목 업데이트")
    parser.add_argument("--db", default="db", help="DB 이름")
    
    args = parser.parse_args()
    target_tickers = args.tickers
    
    if args.all:
        try:
            from AI.libs.database.ticker_loader import load_all_tickers_from_db
            print(">> DB에서 종목 리스트를 불러옵니다...")
            target_tickers = load_all_tickers_from_db(verbose=True)
        except Exception as e:
            print(f"[Error] 종목 로드 실패: {e}")
            sys.exit(1)
            
    if target_tickers:
        update_stock_info(target_tickers, db_name=args.db)
    else:
        print(">> 실행할 종목이 없습니다. (사용법: python stock_info_collector.py --all)")