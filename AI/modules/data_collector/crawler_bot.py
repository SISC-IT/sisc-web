# AI/modules/data_collector/crawler_bot.py
import argparse
import os
import sys
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
from AI.orchestration.legacy import execute_jobs  # noqa: E402


def get_stock_tickers(db_name="db"):
    """
    주식 수집 대상 티커 리스트를 마스터 테이블(stock_info)에서 조회합니다.
    """
    try:
        from AI.libs.database.connection import get_db_conn

        conn = get_db_conn(db_name)
        try:
            with conn.cursor() as cursor:
                cursor.execute("SELECT ticker FROM public.stock_info ORDER BY ticker")
                tickers = [row[0] for row in cursor.fetchall()]
        finally:
            conn.close()
        filtered_tickers = [
            t for t in tickers if not t.startswith("^") and "-USD" not in t
        ]

        if not filtered_tickers:
            print(
                ">> [Init] DB(stock_info)에 주식 종목이 없습니다. 기본 종목(Big Tech)으로 시작합니다."
            )
            return ["AAPL", "TSLA", "NVDA", "MSFT", "GOOGL", "AMD", "INTC"]

        return filtered_tickers

    except Exception as e:
        print(f"[Error] 티커 목록 조회 실패: {e}")
        return ["AAPL", "TSLA"]


def check_db_connection(db_name="db") -> bool:
    from AI.libs.database.connection import get_db_conn

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
    from AI.modules.data_collector.components.ticker_updater import TickerUpdater

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
    parser = argparse.ArgumentParser(
        description="[SISC AI] 통합 데이터 수집 파이프라인"
    )
    parser.add_argument("--db", default="db", help="DB 연결 정보 키")
    parser.add_argument(
        "--tickers", nargs="*", help="특정 주식 티커만 수집 (생략 시 DB 전체)"
    )

    parser.add_argument("--skip-price", action="store_true", help="주식 시세 수집 Skip")
    parser.add_argument("--skip-info", action="store_true", help="주식 정보 수집 Skip")
    parser.add_argument("--skip-fund", action="store_true", help="재무제표 수집 Skip")
    parser.add_argument("--skip-macro", action="store_true", help="거시경제 수집 Skip")
    parser.add_argument("--skip-crypto", action="store_true", help="암호화폐 수집 Skip")
    parser.add_argument(
        "--skip-event", action="store_true", help="이벤트 데이터 수집 Skip"
    )
    parser.add_argument(
        "--skip-breadth", action="store_true", help="시장 폭(ETF) 데이터 수집 Skip"
    )
    parser.add_argument(
        "--skip-stats", action="store_true", help="시장 통계(NH-NL) 계산 Skip"
    )

    parser.add_argument(
        "--repair", action="store_true", help="데이터 누락 복구 모드 (전체 기간 재수집)"
    )

    parser.add_argument(
        "--event-only", action="store_true", help="이벤트 데이터만 수집 (나머지 Skip)"
    )
    parser.add_argument(
        "--market-breadth-only",
        action="store_true",
        help="시장 폭(ETF) 데이터만 수집 (나머지 Skip)",
    )
    parser.add_argument(
        "--stats-only", action="store_true", help="시장 통계 계산만 수행 (나머지 Skip)"
    )

    parser.add_argument(
        "--track-runs",
        action="store_true",
        help="Record legacy run status in ingestion schema (migrate first)",
    )
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

    need_stock_tickers = not (
        args.skip_price and args.skip_info and args.skip_fund and args.skip_event
    )

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

    print("\n========================================================")
    print(f" [SISC Data Collector] 통합 수집 시작 ({datetime.now()})")
    print("========================================================\n")

    start_time = time.time()

    stock_tickers = []
    if need_stock_tickers:
        if args.tickers:
            stock_tickers = args.tickers
        else:
            stock_tickers = get_stock_tickers(args.db)
        print(f">> 타겟 주식 종목 수: {len(stock_tickers)}개")

    store = None
    sha = "unknown"
    if args.track_runs:
        from AI.orchestration.cli import code_sha
        from AI.orchestration.store import Store

        store, sha = Store(args.db), code_sha()
    results = execute_jobs(args, stock_tickers, store=store, code_sha=sha)
    for result in results:
        print(f"[Dataset] {result['dataset']}: {result['status']}")

    elapsed = time.time() - start_time
    print("\n========================================================")
    print(
        f" [종료] Legacy 수집 호출 종료; 데이터 검증 상태는 위 결과 참조 ({elapsed:.2f}초)"
    )
    print("========================================================")

    return 1 if any(r["status"] in {"failed", "locked"} for r in results) else 0


if __name__ == "__main__":
    sys.exit(main())
