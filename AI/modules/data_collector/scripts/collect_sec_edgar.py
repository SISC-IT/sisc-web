from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.append(str(PROJECT_ROOT))

from AI.modules.data_collector.components.sec_edgar_data import (
    DEFAULT_CONFIG_PATH,
    SecEdgarCollectorConfig,
    SecEdgarDataCollector,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="SEC EDGAR에서 8-K와 Form 4 공시를 수집합니다."
    )
    parser.add_argument(
        "--config",
        default=str(DEFAULT_CONFIG_PATH),
        help="SEC 수집기 JSON 설정 파일",
    )
    parser.add_argument(
        "--tickers",
        nargs="*",
        help="수집할 미국주식 티커(예: AAPL MSFT)",
    )
    parser.add_argument(
        "--ciks",
        nargs="*",
        help="티커가 없는 발행사를 위한 SEC CIK",
    )
    parser.add_argument(
        "--forms",
        nargs="*",
        choices=["8-K", "8-K/A", "4", "4/A"],
        help="수집할 공시 양식",
    )
    parser.add_argument(
        "--items",
        nargs="*",
        dest="item_codes",
        choices=["2.02", "5.02"],
        help="8-K에서 수집할 Item 코드",
    )
    parser.add_argument("--start", dest="start_date", help="수집 시작일(YYYY-MM-DD)")
    parser.add_argument("--end", dest="end_date", help="수집 종료일(YYYY-MM-DD)")
    parser.add_argument(
        "--storage",
        choices=["file", "db", "both"],
        help="저장 대상",
    )
    parser.add_argument("--data-dir", help="원문과 메타데이터 저장 경로")
    parser.add_argument("--cache-dir", help="SEC HTTP 응답 캐시 경로")
    parser.add_argument("--log-dir", help="수집 로그 경로")
    parser.add_argument("--db", dest="db_name", help="DB 환경변수 접두사")
    parser.add_argument(
        "--rate",
        dest="max_requests_per_second",
        type=float,
        help="초당 최대 요청 수(10 이하, 기본 8)",
    )
    parser.add_argument(
        "--limit",
        type=int,
        help="검증용 최대 회사 수",
    )
    parser.add_argument(
        "--user-agent",
        help="SEC 식별 User-Agent. SEC_USER_AGENT 환경변수를 권장합니다.",
    )
    parser.add_argument(
        "--all-exhibits",
        dest="include_all_exhibits",
        action="store_true",
        default=None,
        help="8-K의 EX-99 이외 모든 Exhibit도 저장",
    )
    parser.add_argument(
        "--recent-only",
        dest="include_historical_files",
        action="store_false",
        default=None,
        help="Submissions API의 최근 공시만 조회",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    user_agent = args.user_agent or os.getenv("SEC_USER_AGENT")
    config = SecEdgarCollectorConfig.from_file(args.config).with_overrides(
        user_agent=user_agent,
        forms=args.forms,
        item_codes=args.item_codes,
        start_date=args.start_date,
        end_date=args.end_date,
        storage=args.storage,
        data_dir=args.data_dir,
        cache_dir=args.cache_dir,
        log_dir=args.log_dir,
        db_name=args.db_name,
        max_requests_per_second=args.max_requests_per_second,
        include_all_exhibits=args.include_all_exhibits,
        include_historical_files=args.include_historical_files,
    )

    with SecEdgarDataCollector(config) as collector:
        stats = collector.collect(
            tickers=args.tickers,
            ciks=args.ciks,
            limit=args.limit,
        )
    print(f"[SEC EDGAR 수집기] 수집 완료: {stats}")


if __name__ == "__main__":
    main()
