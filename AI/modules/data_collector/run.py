# AI/modules/data_collector/run.py
import sys
import os
import argparse
import time
from datetime import datetime

# -----------------------------------------------------------
# [경로 설정 수정]
# 파일 위치가 깊어졌으므로 프로젝트 루트를 찾기 위해 3단계 상위로 이동합니다.
# (run.py -> data_collector -> modules -> AI -> 프로젝트 루트)
# -----------------------------------------------------------
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))

if project_root not in sys.path:
    sys.path.append(project_root)

# -----------------------------------------------------------
# [모듈 임포트] 
# -----------------------------------------------------------
from AI.libs.database.connection import get_db_conn
# 같은 폴더 내에 있지만, 프로젝트 루트를 기준으로 절대 경로 import를 권장합니다.
from AI.modules.data_collector.market_data import update_market_data          
from AI.modules.data_collector.stock_info_collector import update_stock_info  
from AI.modules.data_collector.company_fundamentals_data import update_company_fundamentals 
from AI.modules.data_collector.macro_data import update_macro_data            

def get_target_tickers(db_name="db"):
    """
    수집 대상 티커 리스트를 DB에서 조회합니다.
    """
    conn = get_db_conn(db_name)
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT DISTINCT ticker FROM public.price_data")
        rows = cursor.fetchall()
        tickers = [r[0] for r in rows]
        
        if not tickers:
            print(">> DB에 종목이 없습니다. 기본 종목(AAPL, TSLA 등)으로 시작합니다.")
            return ["AAPL", "TSLA", "NVDA", "MSFT", "GOOGL", "AMD", "INTC"]
            
        return tickers
    except Exception as e:
        print(f"[Error] 티커 목록 조회 실패: {e}")
        return []
    finally:
        cursor.close()
        conn.close()

def main():
    parser = argparse.ArgumentParser(description="[SISC AI] 통합 데이터 수집 파이프라인")
    parser.add_argument("--db", default="db", help="DB 연결 정보 키")
    parser.add_argument("--tickers", nargs="*", help="특정 티커만 수집 (생략 시 전체)")
    
    # 개별 수집 스킵 옵션
    parser.add_argument("--skip-price", action="store_true", help="주가 데이터 수집 건너뛰기")
    parser.add_argument("--skip-info", action="store_true", help="주식 정보 수집 건너뛰기")
    parser.add_argument("--skip-fund", action="store_true", help="재무제표 수집 건너뛰기")
    parser.add_argument("--skip-macro", action="store_true", help="거시경제 지표 수집 건너뛰기")
    
    args = parser.parse_args()

    # 1. 대상 종목 선정
    if args.tickers:
        target_tickers = args.tickers
    else:
        print(">> 전체 관리 종목을 조회합니다...")
        target_tickers = get_target_tickers(args.db)
    
    if not target_tickers:
        print(">> 수집할 종목이 없어 종료합니다.")
        return

    print(f"\n========================================================")
    print(f" [SISC Data Collector] 통합 수집 시작 ({datetime.now()})")
    print(f" 대상 종목 수: {len(target_tickers)}개")
    print(f"========================================================\n")

    start_time = time.time()

    # -------------------------------------------------------
    # 2. 순차적 데이터 수집 실행
    # -------------------------------------------------------
    
    # (1) 주가 데이터 (OHLCV)
    if not args.skip_price:
        try:
            print("\n>>> [Step 1] 주가 데이터(OHLCV) 업데이트")
            update_market_data(target_tickers, db_name=args.db, repair_mode=False)
        except Exception as e:
            print(f"[Error] Market Data 수집 중단: {e}")
    else:
        print("\n>>> [Step 1] 주가 데이터 수집 Skip")

    # (2) 주식 기본 정보 (Stock Info)
    if not args.skip_info:
        try:
            print("\n>>> [Step 2] 주식 기본 정보(Stock Info) 업데이트")
            update_stock_info(target_tickers, db_name=args.db)
        except Exception as e:
            print(f"[Error] Stock Info 수집 중단: {e}")
    else:
        print("\n>>> [Step 2] 주식 정보 수집 Skip")

    # (3) 재무제표 (Fundamentals)
    if not args.skip_fund:
        try:
            print("\n>>> [Step 3] 재무제표(Fundamentals) 업데이트")
            update_company_fundamentals(target_tickers, db_name=args.db)
        except Exception as e:
            print(f"[Error] Fundamentals 수집 중단: {e}")
    else:
        print("\n>>> [Step 3] 재무제표 수집 Skip")

    # (4) 거시경제 지표 (Macro)
    if not args.skip_macro:
        try:
            print("\n>>> [Step 4] 거시경제 지표(Macro) 업데이트")
            update_macro_data(db_name=args.db)
        except Exception as e:
            print(f"[Error] Macro Data 수집 중단: {e}")
    else:
        print("\n>>> [Step 4] 거시경제 지표 수집 Skip")

    elapsed = time.time() - start_time
    print(f"\n========================================================")
    print(f" [완료] 모든 데이터 수집 종료 (총 소요시간: {elapsed:.2f}초)")
    print(f"========================================================")

if __name__ == "__main__":
    main()