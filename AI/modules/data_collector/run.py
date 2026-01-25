# AI/modules/data_collector/run.py
import sys
import os
import argparse
import time
from datetime import datetime

# -----------------------------------------------------------
# [경로 설정]
# -----------------------------------------------------------
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))

if project_root not in sys.path:
    sys.path.append(project_root)

# -----------------------------------------------------------
# [모듈 임포트] 
# -----------------------------------------------------------
from AI.libs.database.connection import get_db_conn

from AI.modules.data_collector.market_data import MarketDataCollector
from AI.modules.data_collector.stock_info_collector import StockInfoCollector
from AI.modules.data_collector.company_fundamentals_data import FundamentalsDataCollector
from AI.modules.data_collector.macro_data import MacroDataCollector
from AI.modules.data_collector.crypto_data import CryptoDataCollector
from AI.modules.data_collector.index_data import IndexDataCollector
from AI.modules.data_collector.event_data import EventDataCollector

def get_stock_tickers(db_name="db"):
    """
    주식 수집 대상 티커 리스트를 DB에서 조회합니다.
    """
    conn = get_db_conn(db_name)
    cursor = conn.cursor()
    try:
        # price_data 테이블에 있는 종목들 조회
        cursor.execute("SELECT DISTINCT ticker FROM public.price_data")
        rows = cursor.fetchall()
        tickers = [r[0] for r in rows if not r[0].startswith('^') and '-USD' not in r[0]] 
        
        if not tickers:
            print(">> [Init] DB에 주식 종목이 없습니다. 기본 종목(Big Tech)으로 시작합니다.")
            return ["AAPL", "TSLA", "NVDA", "MSFT", "GOOGL", "AMD", "INTC"]
            
        return tickers
    except Exception as e:
        print(f"[Error] 티커 목록 조회 실패: {e}")
        return ["AAPL", "TSLA"]
    finally:
        if cursor: cursor.close()
        if conn: conn.close()

def main():
    parser = argparse.ArgumentParser(description="[SISC AI] 통합 데이터 수집 파이프라인")
    parser.add_argument("--db", default="db", help="DB 연결 정보 키")
    parser.add_argument("--tickers", nargs="*", help="특정 주식 티커만 수집 (생략 시 DB 전체)")
    
    # 모듈별 스킵 옵션
    parser.add_argument("--skip-price", action="store_true", help="주식 시세 수집 Skip")
    parser.add_argument("--skip-index", action="store_true", help="시장 지수 수집 Skip")
    parser.add_argument("--skip-info", action="store_true", help="주식 정보 수집 Skip")
    parser.add_argument("--skip-fund", action="store_true", help="재무제표 수집 Skip")
    parser.add_argument("--skip-macro", action="store_true", help="거시경제 수집 Skip")
    parser.add_argument("--skip-crypto", action="store_true", help="암호화폐 수집 Skip")
    parser.add_argument("--skip-event", action="store_true", help="이벤트 데이터 수집 Skip")
    
    parser.add_argument("--repair", action="store_true", help="데이터 누락 복구 모드 (전체 기간 재수집)")
    parser.add_argument("--event-only", action="store_true", help="이벤트 데이터만 수집 (나머지 Skip)")
    
    args = parser.parse_args()

    if args.event_only:
        args.skip_price = True
        args.skip_index = True
        args.skip_info = True
        args.skip_fund = True
        args.skip_macro = True
        args.skip_crypto = True
        args.skip_event = False
        print(">> [Mode] Event Data Only 모드로 실행합니다.")

    print(f"\n========================================================")
    print(f" [SISC Data Collector] 통합 수집 시작 ({datetime.now()})")
    print(f"========================================================\n")

    start_time = time.time()

    # 1. 주식 종목 선정
    if args.tickers:
        stock_tickers = args.tickers
    else:
        stock_tickers = get_stock_tickers(args.db)
    
    if not args.event_only:
        print(f">> 타겟 주식 종목 수: {len(stock_tickers)}개")

    # -------------------------------------------------------
    # 2. 순차적 데이터 수집 실행
    # -------------------------------------------------------
    
    # (1) 거시경제 지표 (Macro)
    if not args.skip_macro:
        try:
            print("\n>>> [Step 1] 거시경제 지표(Macro) 업데이트")
            collector = MacroDataCollector(db_name=args.db)
            collector.run(lookback_days=365*5 if args.repair else 365*2)
        except Exception as e:
            print(f"[Error] Macro Data 수집 중단: {e}")
    
    # (2) 시장 지수 (Index)
    if not args.skip_index:
        try:
            print("\n>>> [Step 2] 시장 지수(Index) 업데이트")
            collector = IndexDataCollector(db_name=args.db)
            collector.run(repair_mode=args.repair)
        except Exception as e:
            print(f"[Error] Index Data 수집 중단: {e}")

    # (3) 주가 데이터 (Stocks OHLCV)
    if not args.skip_price and stock_tickers:
        try:
            print("\n>>> [Step 3] 개별 주식 시세(OHLCV) 업데이트")
            collector = MarketDataCollector(db_name=args.db)
            collector.update_tickers(stock_tickers, repair_mode=args.repair)
        except Exception as e:
            print(f"[Error] Market Data 수집 중단: {e}")

    # (4) 암호화폐 데이터 (Crypto)
    if not args.skip_crypto:
        try:
            print("\n>>> [Step 4] 암호화폐(Crypto) 업데이트")
            target_crypto = ["BTC-USD", "ETH-USD"]
            collector = CryptoDataCollector(db_name=args.db)
            collector.update_tickers(target_crypto, repair_mode=args.repair)
        except Exception as e:
            print(f"[Error] Crypto Data 수집 중단: {e}")

    # (5) 재무제표 (Fundamentals)
    if not args.skip_fund and stock_tickers:
        try:
            print("\n>>> [Step 5] 기업 재무제표(Fundamentals) 업데이트")
            collector = FundamentalsDataCollector(db_name=args.db)
            collector.update_tickers(stock_tickers)
        except Exception as e:
            print(f"[Error] Fundamentals 수집 중단: {e}")

    # (6) 주식 기본 정보 (Stock Info)
    if not args.skip_info and stock_tickers:
        try:
            print("\n>>> [Step 6] 주식 정보(Stock Info) 업데이트")
            collector = StockInfoCollector(db_name=args.db)
            collector.update_tickers(stock_tickers)
        except Exception as e:
            print(f"[Error] Stock Info 수집 중단: {e}")

    # (7) 이벤트 일정 (Earnings, Macro Events)
    if not args.skip_event:
        try:
            print("\n>>> [Step 7] 이벤트 일정(Event Data) 업데이트")
            collector = EventDataCollector(db_name=args.db)
            
            # A. 거시경제 일정 (repair 모드일 때 API 강제 호출)
            collector.update_macro_events(force_update=args.repair)
            
            # B. 기업 실적 발표일 (주식 종목 대상)
            if stock_tickers:
                collector.update_earnings_dates(stock_tickers)
                
        except Exception as e:
            print(f"[Error] Event Data 수집 중단: {e}")

    elapsed = time.time() - start_time
    print(f"\n========================================================")
    print(f" [완료] 모든 작업 종료 (총 소요시간: {elapsed:.2f}초)")
    print(f"========================================================")

if __name__ == "__main__":
    main()