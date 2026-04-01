# AI/modules/data_collector/crawler_bot.py
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
from AI.libs.database.ticker_loader import load_all_tickers_from_db
from AI.modules.data_collector.components.market_data import MarketDataCollector
from AI.modules.data_collector.components.stock_info_collector import StockInfoCollector
from AI.modules.data_collector.components.company_fundamentals_data import FundamentalsDataCollector
from AI.modules.data_collector.components.macro_data import MacroDataCollector
from AI.modules.data_collector.components.crypto_data import CryptoDataCollector
from AI.modules.data_collector.components.event_data import EventDataCollector
from AI.modules.data_collector.components.market_breadth_data import MarketBreadthCollector
from AI.modules.data_collector.components.market_breadth_stats import MarketBreadthStatsCollector
from AI.modules.data_collector.components.ticker_updater import TickerUpdater

def get_stock_tickers(db_name="db"):
    """
    주식 수집 대상 티커 리스트를 마스터 테이블(stock_info)에서 조회합니다.
    """
    try:
        tickers = load_all_tickers_from_db(verbose=False)
        filtered_tickers = [t for t in tickers if not t.startswith('^') and '-USD' not in t]
        
        if not filtered_tickers:
            print(">> [Init] DB(stock_info)에 주식 종목이 없습니다. 기본 종목(Big Tech)으로 시작합니다.")
            return ["AAPL", "TSLA", "NVDA", "MSFT", "GOOGL", "AMD", "INTC"]
            
        return filtered_tickers
        
    except Exception as e:
        print(f"[Error] 티커 목록 조회 실패: {e}")
        return ["AAPL", "TSLA"]


def check_db_connection(db_name="db") -> bool:
    conn = None
    try:
        conn = get_db_conn(db_name)
        cursor = conn.cursor()
        cursor.execute("SELECT 1;")
        cursor.fetchone()
        cursor.close()
        print(">> [Init] DB 연결 확인 성공")
        return True
    except Exception as e:
        print(f"[Fatal] DB 연결 실패: {e}")
        return False
    finally:
        if conn:
            conn.close()


def bootstrap_tickers_if_needed(db_name="db", threshold=100):
    """
    1) crawler 시작 시 ticker_updater를 실행
    2) stock_info 티커 수가 threshold 이하일 때만 수집 시도
    """
    updater = TickerUpdater(db_name=db_name)

    try:
        current_count = updater.count_tickers_in_db()
    except Exception as e:
        print(f"[Error] DB 티커 수 확인 실패: {e}")
        return

    print(f">> [Init] DB(stock_info) 티커 수: {current_count}개")
    if current_count > threshold:
        print(f">> [Init] 티커 수 {threshold}개 초과로 ticker_updater 생략")
        return

    print(f">> [Init] 티커 수 {threshold}개 이하로 ticker_updater 실행")
    updater.ensure_minimum_tickers(min_count=threshold, source="all")

def main():
    parser = argparse.ArgumentParser(description="[SISC AI] 통합 데이터 수집 파이프라인")
    parser.add_argument("--db", default="db", help="DB 연결 정보 키")
    parser.add_argument("--tickers", nargs="*", help="특정 주식 티커만 수집 (생략 시 DB 전체)")
    
    parser.add_argument("--skip-price", action="store_true", help="주식 시세 수집 Skip")
    parser.add_argument("--skip-info", action="store_true", help="주식 정보 수집 Skip")
    parser.add_argument("--skip-fund", action="store_true", help="재무제표 수집 Skip")
    parser.add_argument("--skip-macro", action="store_true", help="거시경제 수집 Skip")
    parser.add_argument("--skip-crypto", action="store_true", help="암호화폐 수집 Skip")
    parser.add_argument("--skip-event", action="store_true", help="이벤트 데이터 수집 Skip")
    parser.add_argument("--skip-breadth", action="store_true", help="시장 폭(ETF) 데이터 수집 Skip")
    parser.add_argument("--skip-stats", action="store_true", help="시장 통계(NH-NL) 계산 Skip")

    parser.add_argument("--repair", action="store_true", help="데이터 누락 복구 모드 (전체 기간 재수집)")
    
    parser.add_argument("--event-only", action="store_true", help="이벤트 데이터만 수집 (나머지 Skip)")
    parser.add_argument("--market-breadth-only", action="store_true", help="시장 폭(ETF) 데이터만 수집 (나머지 Skip)")
    parser.add_argument("--stats-only", action="store_true", help="시장 통계 계산만 수행 (나머지 Skip)")
    
    args = parser.parse_args()

    # -------------------------------------------------------
    # [Only 모드 로직 처리]
    # -------------------------------------------------------
    if args.stats_only:
        args.skip_price = args.skip_info = args.skip_fund = args.skip_macro = True
        args.skip_crypto = args.skip_event = args.skip_breadth = True
        args.skip_stats = False
        print(">> [Mode] Market Stats Calculation Only 모드")
    
    if args.event_only:
        args.skip_price = args.skip_info = args.skip_fund = args.skip_macro = True
        args.skip_crypto = args.skip_breadth = args.skip_stats = True
        args.skip_event = False
        print(">> [Mode] Event Data Only 모드로 실행합니다.")

    if args.market_breadth_only:
        args.skip_price = args.skip_info = args.skip_fund = args.skip_macro = True
        args.skip_crypto = args.skip_event = args.skip_stats = True
        args.skip_breadth = False
        print(">> [Mode] Market Breadth Only 모드로 실행합니다.")

    need_stock_tickers = not (args.skip_price and args.skip_info and args.skip_fund and args.skip_event)

    # -------------------------------------------------------
    # [Step 0] DB 연결 / ticker_updater 분기
    # -------------------------------------------------------
    if not check_db_connection(args.db):
        print("[CrawlerBot] DB 연결 실패로 크롤러 봇을 종료합니다.")
        sys.exit(1)

    if need_stock_tickers:
        bootstrap_tickers_if_needed(db_name=args.db, threshold=100)
    else:
        print(">> [Init] 주식 티커가 필요 없는 모드로 ticker_updater를 생략합니다.")

    print(f"\n========================================================")
    print(f" [SISC Data Collector] 통합 수집 시작 ({datetime.now()})")
    print(f"========================================================\n")

    start_time = time.time()

    stock_tickers = []
    if need_stock_tickers:
        if args.tickers:
            stock_tickers = args.tickers
        else:
            stock_tickers = get_stock_tickers(args.db)
        print(f">> 타겟 주식 종목 수: {len(stock_tickers)}개")

    # -------------------------------------------------------
    # 2. 순차적 데이터 수집 실행
    # -------------------------------------------------------
    
    if not args.skip_macro:
        try:
            print("\n>>> [Step 1] 거시경제 지표(Macro) 업데이트")
            collector = MacroDataCollector(db_name=args.db)
            collector.run(lookback_days=365*10 if args.repair else 365*2)
        except Exception as e:
            print(f"[Error] Macro Data 수집 중단: {e}")

    if not args.skip_info and stock_tickers:
        try:
            print("\n>>> [Step 2] 주식 정보(Stock Info) 업데이트")
            collector = StockInfoCollector(db_name=args.db)
            collector.update_tickers(stock_tickers)
        except Exception as e:
            print(f"[Error] Stock Info 수집 중단: {e}")

    if not args.skip_fund and stock_tickers:
        try:
            print("\n>>> [Step 3] 기업 재무제표(Fundamentals) 업데이트")
            collector = FundamentalsDataCollector(db_name=args.db)
            collector.update_tickers(stock_tickers)
        except Exception as e:
            print(f"[Error] Fundamentals 수집 중단: {e}")

    if not args.skip_price and stock_tickers:
        try:
            print("\n>>> [Step 4] 개별 주식 시세(OHLCV) 업데이트")
            collector = MarketDataCollector(db_name=args.db)
            collector.update_tickers(stock_tickers, repair_mode=args.repair)
        except Exception as e:
            print(f"[Error] Market Data 수집 중단: {e}")

    if not args.skip_crypto:
        try:
            print("\n>>> [Step 5] 암호화폐(Crypto) 업데이트")
            target_crypto = ["BTC-USD", "ETH-USD"]
            collector = CryptoDataCollector(db_name=args.db)
            collector.update_tickers(target_crypto, repair_mode=args.repair)
        except Exception as e:
            print(f"[Error] Crypto Data 수집 중단: {e}")

    if not args.skip_event:
        try:
            print("\n>>> [Step 6] 이벤트 일정(Event Data) 업데이트")
            collector = EventDataCollector(db_name=args.db)
            collector.update_macro_events(force_update=args.repair)
            if stock_tickers:
                collector.update_earnings_dates(stock_tickers)
        except Exception as e:
            print(f"[Error] Event Data 수집 중단: {e}")

    if not args.skip_breadth:
        try:
            print("\n>>> [Step 7] 시장 폭 및 섹터 데이터(Sector Returns) 업데이트")
            collector = MarketBreadthCollector(db_name=args.db)
            collector.run(repair_mode=args.repair)
        except Exception as e:
            print(f"[Error] Sector Data 수집 중단: {e}")

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
