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
from AI.modules.data_collector.event_data import EventDataCollector
from AI.modules.data_collector.market_breadth_data import MarketBreadthCollector
from AI.modules.data_collector.market_breadth_stats import MarketBreadthStatsCollector

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
    parser.add_argument("--skip-info", action="store_true", help="주식 정보 수집 Skip")
    parser.add_argument("--skip-fund", action="store_true", help="재무제표 수집 Skip")
    parser.add_argument("--skip-macro", action="store_true", help="거시경제 수집 Skip")
    parser.add_argument("--skip-crypto", action="store_true", help="암호화폐 수집 Skip")
    parser.add_argument("--skip-event", action="store_true", help="이벤트 데이터 수집 Skip")
    parser.add_argument("--skip-breadth", action="store_true", help="시장 폭(ETF) 데이터 수집 Skip")
    parser.add_argument("--skip-stats", action="store_true", help="시장 통계(NH-NL) 계산 Skip")

    parser.add_argument("--repair", action="store_true", help="데이터 누락 복구 모드 (전체 기간 재수집)")
    
    # 단독 실행 모드 옵션
    parser.add_argument("--event-only", action="store_true", help="이벤트 데이터만 수집 (나머지 Skip)")
    parser.add_argument("--market-breadth-only", action="store_true", help="시장 폭(ETF) 데이터만 수집 (나머지 Skip)")
    parser.add_argument("--stats-only", action="store_true", help="시장 통계 계산만 수행 (나머지 Skip)")
    
    args = parser.parse_args()

    # -------------------------------------------------------
    # [Only 모드 로직 처리]
    # 특정 모드가 켜지면 나머지 skip 옵션을 강제로 True로 설정
    # -------------------------------------------------------
    
    # 1. Stats Only
    if args.stats_only:
        args.skip_price = True
        args.skip_info = True
        args.skip_fund = True
        args.skip_macro = True
        args.skip_crypto = True
        args.skip_event = True
        args.skip_breadth = True
        args.skip_stats = False # 통계만 실행
        print(">> [Mode] Market Stats Calculation Only 모드")
    
    # 2. Event Only
    if args.event_only:
        args.skip_price = True
        args.skip_info = True
        args.skip_fund = True
        args.skip_macro = True
        args.skip_crypto = True
        args.skip_breadth = True
        args.skip_stats = True  # [수정] 통계 계산도 Skip 해야 함
        args.skip_event = False
        print(">> [Mode] Event Data Only 모드로 실행합니다.")

    # 3. Market Breadth Only (ETF Sector Returns)
    if args.market_breadth_only:
        args.skip_price = True
        args.skip_info = True
        args.skip_fund = True
        args.skip_macro = True
        args.skip_crypto = True
        args.skip_event = True
        args.skip_stats = True  # [수정] 통계 계산도 Skip 해야 함
        args.skip_breadth = False
        print(">> [Mode] Market Breadth Only 모드로 실행합니다.")

    print(f"\n========================================================")
    print(f" [SISC Data Collector] 통합 수집 시작 ({datetime.now()})")
    print(f"========================================================\n")

    start_time = time.time()

    # 1. 주식 종목 선정 (Price, Info, Fund, Event 단계에서만 필요)
    # Stats 단계는 내부 데이터만 쓰므로 티커 리스트 불필요
    need_stock_tickers = not (args.skip_price and args.skip_info and args.skip_fund and args.skip_event)
    
    stock_tickers = []
    if need_stock_tickers:
        if args.tickers:
            stock_tickers = args.tickers
        else:
            stock_tickers = get_stock_tickers(args.db)
        print(f">> 타겟 주식 종목 수: {len(stock_tickers)}개")
    else:
        print(">> 개별 주식 티커 조회를 건너뜁니다.")

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

    # (2) 주가 데이터 (Stocks OHLCV)
    if not args.skip_price and stock_tickers:
        try:
            print("\n>>> [Step 2] 개별 주식 시세(OHLCV) 업데이트")
            collector = MarketDataCollector(db_name=args.db)
            collector.update_tickers(stock_tickers, repair_mode=args.repair)
        except Exception as e:
            print(f"[Error] Market Data 수집 중단: {e}")

    # (3) 암호화폐 데이터 (Crypto)
    if not args.skip_crypto:
        try:
            print("\n>>> [Step 3] 암호화폐(Crypto) 업데이트")
            target_crypto = ["BTC-USD", "ETH-USD"]
            collector = CryptoDataCollector(db_name=args.db)
            collector.update_tickers(target_crypto, repair_mode=args.repair)
        except Exception as e:
            print(f"[Error] Crypto Data 수집 중단: {e}")

    # (4) 재무제표 (Fundamentals)
    if not args.skip_fund and stock_tickers:
        try:
            print("\n>>> [Step 4] 기업 재무제표(Fundamentals) 업데이트")
            collector = FundamentalsDataCollector(db_name=args.db)
            collector.update_tickers(stock_tickers)
        except Exception as e:
            print(f"[Error] Fundamentals 수집 중단: {e}")

    # (5) 주식 기본 정보 (Stock Info)
    if not args.skip_info and stock_tickers:
        try:
            print("\n>>> [Step 5] 주식 정보(Stock Info) 업데이트")
            collector = StockInfoCollector(db_name=args.db)
            collector.update_tickers(stock_tickers)
        except Exception as e:
            print(f"[Error] Stock Info 수집 중단: {e}")

    # (6) 이벤트 일정 (Earnings, Macro Events)
    if not args.skip_event:
        try:
            print("\n>>> [Step 6] 이벤트 일정(Event Data) 업데이트")
            collector = EventDataCollector(db_name=args.db)
            collector.update_macro_events(force_update=args.repair)
            if stock_tickers:
                collector.update_earnings_dates(stock_tickers)
        except Exception as e:
            print(f"[Error] Event Data 수집 중단: {e}")

    # (7) 시장 폭 및 섹터 데이터 (Market Breadth - Sector Returns)
    if not args.skip_breadth:
        try:
            print("\n>>> [Step 9] 시장 폭 및 섹터 데이터(Sector Returns) 업데이트")
            collector = MarketBreadthCollector(db_name=args.db)
            collector.run(repair_mode=args.repair)
        except Exception as e:
            print(f"[Error] Sector Data 수집 중단: {e}")

    # (8) 시장 통계 계산 (Market Breadth Stats - Internal Aggregation)
    if not args.skip_stats:
        try:
            print("\n>>> [Step 8] 시장 통계(NH-NL, MA200%) 계산 및 저장")
            collector = MarketBreadthStatsCollector(db_name=args.db)
            collector.run(repair_mode=args.repair)
        except Exception as e:
            print(f"[Error] Market Stats 계산 중단: {e}")

    elapsed = time.time() - start_time
    print(f"\n========================================================")
    print(f" [완료] 모든 작업 종료 (총 소요시간: {elapsed:.2f}초)")
    print(f"========================================================")

if __name__ == "__main__":
    main()