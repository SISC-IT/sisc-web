from __future__ import annotations

import argparse
import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    # 스크립트를 어느 위치에서 실행해도 `AI...` 패키지 import가 되도록 저장소 루트를 path에 추가합니다.
    sys.path.append(str(PROJECT_ROOT))

from AI.modules.data_collector.components.korea_stock_data import (
    DEFAULT_CONFIG_PATH,
    KoreaStockCollectorConfig,
    KoreaStockDataCollector,
)


def parse_args() -> argparse.Namespace:
    """국내 주식 수집 CLI 인자를 정의합니다."""

    parser = argparse.ArgumentParser(description="KOSPI/KOSDAQ 일봉 OHLCV 데이터를 수집합니다.")

    # 설정 파일을 기본값으로 두고, CLI 인자가 들어오면 필요한 값만 덮어씁니다.
    parser.add_argument("--config", default=str(DEFAULT_CONFIG_PATH), help="수집기 JSON 설정 파일 경로")

    # source=fdr이 기본값입니다. pykrx는 별도 환경에서 선택적으로 사용할 수 있습니다.
    parser.add_argument("--source", choices=["fdr", "pykrx"], help="데이터 소스(기본값: 설정 파일 값)")

    # 시장 전체 수집용 옵션입니다. 생략하면 config의 KOSPI/KOSDAQ 기본값을 씁니다.
    parser.add_argument("--markets", nargs="*", choices=["KOSPI", "KOSDAQ", "KONEX", "ALL"], help="수집 대상 시장")

    # 특정 종목만 빠르게 검증하거나 재수집할 때 사용합니다. 예: 005930 000660
    parser.add_argument("--tickers", nargs="*", help="수집할 국내주식 6자리 종목 코드")

    # 기간을 명시하지 않으면 config의 start_date와 오늘 날짜를 사용합니다.
    parser.add_argument("--start", dest="start_date", help="수집 시작일(예: 2024-01-01)")
    parser.add_argument("--end", dest="end_date", help="수집 종료일(예: 2024-12-31, 기본값: 오늘)")

    # file은 로컬 검증, db는 운영 파이프라인 적재, both는 둘 다 수행합니다.
    parser.add_argument("--storage", choices=["file", "db", "both"], help="저장 대상")
    parser.add_argument("--format", dest="file_format", choices=["csv", "parquet"], help="파일 저장 형식")

    # 산출물/로그 경로와 DB prefix를 실행 시점에 바꿀 수 있게 열어둡니다.
    parser.add_argument("--data-dir", help="CSV/Parquet 파일 저장 경로")
    parser.add_argument("--log-dir", help="로그 저장 경로")
    parser.add_argument("--db", dest="db_name", help="DB 환경변수 접두사(예: db)")

    # 전체 종목 수집 전 샘플 검증을 쉽게 하기 위한 옵션입니다.
    parser.add_argument("--limit", dest="batch_limit", type=int, help="테스트용 최대 수집 종목 수")

    # 공개 데이터 소스에 요청을 너무 빠르게 보내지 않도록 sleep 값을 조절할 수 있습니다.
    parser.add_argument("--sleep", dest="sleep_seconds", type=float, help="종목별 요청 사이의 대기 시간(초)")
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    # 1. JSON 설정을 읽습니다.
    # 2. CLI에서 명시한 값만 덮어씁니다.
    # 3. 완성된 설정으로 Collector를 실행합니다.
    config = KoreaStockCollectorConfig.from_file(args.config).with_overrides(
        source=args.source,
        markets=tuple(args.markets) if args.markets else None,
        start_date=args.start_date,
        end_date=args.end_date,
        storage=args.storage,
        file_format=args.file_format,
        data_dir=args.data_dir,
        log_dir=args.log_dir,
        db_name=args.db_name,
        batch_limit=args.batch_limit,
        sleep_seconds=args.sleep_seconds,
    )

    collector = KoreaStockDataCollector(config)

    # batch 수집은 내부적으로 종목별 예외를 잡아 실패 로그로 남기고 다음 종목을 계속 처리합니다.
    stats = collector.collect_batch(
        tickers=args.tickers,
        markets=config.markets,
        start_date=config.start_date,
        end_date=config.end_date,
    )
    print(f"[국내주식 수집기] 수집 완료: {stats}")


if __name__ == "__main__":
    main()
