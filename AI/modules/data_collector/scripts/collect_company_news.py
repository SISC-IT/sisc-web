from __future__ import annotations

# ruff: noqa: E402

import argparse
import json
import sys
from dataclasses import replace
from datetime import datetime, timezone
from pathlib import Path

from psycopg2 import Error as DatabaseError

PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.append(str(PROJECT_ROOT))

from AI.modules.data_collector.components.news.config import (
    DEFAULT_CONFIG_PATH,
    NewsCollectionConfig,
    load_company_universe,
)
from AI.modules.data_collector.components.news.contracts import CollectionStatus
from AI.modules.data_collector.components.news.pipeline import CompanyNewsCollector
from AI.modules.data_collector.components.news.providers.google_news_rss import (
    GoogleNewsRssProvider,
)
from AI.modules.data_collector.components.news.repository import (
    CollectionAlreadyRunningError,
    NewsRepository,
)
from AI.modules.data_collector.components.news.windows import (
    forward_collection_window,
    require_aware_utc,
)

EXIT_SUCCESS = 0
EXIT_PARTIAL_SUCCESS = 2
EXIT_PROVIDER_ERROR = 3
EXIT_INVALID_CONFIG = 4
EXIT_ALREADY_RUNNING = 5
EXIT_PERSISTENCE_ERROR = 6


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "SEC 이벤트와 독립적으로 S&P 100 issuer의 최근 미국 뉴스를 "
            "한 번 수집해 JSON으로 출력합니다."
        )
    )
    parser.add_argument(
        "--config",
        default=str(DEFAULT_CONFIG_PATH),
        help="뉴스 수집 설정 JSON 경로",
    )
    parser.add_argument(
        "--universe",
        help="S&P 100 snapshot JSON 경로(config의 universe_file을 덮어씀)",
    )
    parser.add_argument(
        "--tickers",
        nargs="+",
        help="전체 universe 대신 선택할 ticker(예: AAPL MSFT GOOGL)",
    )
    parser.add_argument(
        "--lookback-hours",
        type=int,
        help="종료 시각부터 조회할 시간(기본 2, 최대 config의 72)",
    )
    parser.add_argument(
        "--start",
        help="명시적 조회 시작 시각(ISO-8601 timezone 필수)",
    )
    parser.add_argument(
        "--end",
        help="조회 종료 시각(ISO-8601 timezone 필수, 기본 현재 UTC)",
    )
    parser.add_argument(
        "--limit-per-company",
        type=int,
        help="관련도·중복 처리 후 issuer별 최대 출력 기사 수",
    )
    parser.add_argument(
        "--output",
        help="결과 JSON 파일 경로(생략하면 stdout)",
    )
    parser.add_argument(
        "--persist",
        action="store_true",
        help="수집 결과를 PostgreSQL에 idempotent upsert",
    )
    parser.add_argument(
        "--db-name",
        default="db",
        help="DB 환경변수 prefix 이름(기본 db -> DB_*)",
    )
    return parser.parse_args(argv)


def _parse_datetime(value: str, field_name: str) -> datetime:
    normalized = value.strip()
    if normalized.endswith("Z"):
        normalized = normalized[:-1] + "+00:00"
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise ValueError(f"{field_name}은 유효한 ISO-8601 시각이어야 합니다.") from exc
    return require_aware_utc(parsed, field_name)


def _select_companies(companies, requested_tickers: list[str] | None):
    if not requested_tickers:
        return tuple(company for company in companies if company.enabled)

    requested = {ticker.upper() for ticker in requested_tickers}
    selected = tuple(
        company
        for company in companies
        if company.enabled and requested.intersection(company.tickers)
    )
    known = {
        ticker
        for company in companies
        for ticker in company.tickers
        if ticker in requested
    }
    unknown = requested.difference(known)
    if unknown:
        raise ValueError(f"universe에 없는 ticker: {', '.join(sorted(unknown))}")
    return selected


def _resolve_window(
    args: argparse.Namespace,
    config: NewsCollectionConfig,
) -> tuple[datetime, datetime]:
    if args.start and args.lookback_hours is not None:
        raise ValueError("--start와 --lookback-hours는 함께 사용할 수 없습니다.")
    end_at = (
        _parse_datetime(args.end, "--end") if args.end else datetime.now(timezone.utc)
    )
    if args.start:
        start_at = _parse_datetime(args.start, "--start")
        if start_at > end_at:
            raise ValueError("--start는 --end보다 늦을 수 없습니다.")
        range_hours = (end_at - start_at).total_seconds() / 3600
        if range_hours > config.max_recovery_hours:
            raise ValueError(
                f"Google RSS 조회 구간은 최대 {config.max_recovery_hours}시간입니다. "
                "5년 백필에는 역사 조회 provider가 필요합니다."
            )
        return start_at, end_at

    lookback_hours = (
        args.lookback_hours
        if args.lookback_hours is not None
        else config.default_lookback_hours
    )
    return forward_collection_window(
        end_at,
        lookback_hours=lookback_hours,
        max_recovery_hours=config.max_recovery_hours,
    )


def _exit_code(status: CollectionStatus) -> int:
    if status == CollectionStatus.PARTIAL_SUCCESS:
        return EXIT_PARTIAL_SUCCESS
    if status == CollectionStatus.PROVIDER_ERROR:
        return EXIT_PROVIDER_ERROR
    if status == CollectionStatus.INVALID_CONFIG:
        return EXIT_INVALID_CONFIG
    return EXIT_SUCCESS


def collect_result(args: argparse.Namespace):
    config = NewsCollectionConfig.from_file(args.config)
    universe = load_company_universe(args.universe or config.universe_file)
    companies = _select_companies(universe.companies, args.tickers)
    if not companies:
        raise ValueError("수집할 enabled 회사가 없습니다.")
    start_at, end_at = _resolve_window(args, config)
    limit = (
        args.limit_per_company
        if args.limit_per_company is not None
        else config.per_company_limit
    )
    if limit <= 0:
        raise ValueError("--limit-per-company는 0보다 커야 합니다.")

    provider = GoogleNewsRssProvider(
        language=config.language,
        country=config.country,
        edition=config.edition,
        connect_timeout_seconds=config.connect_timeout_seconds,
        read_timeout_seconds=config.read_timeout_seconds,
        max_response_bytes=config.max_response_bytes,
    )
    collector = CompanyNewsCollector(
        provider,
        company_relevance_threshold=config.company_relevance_threshold,
        inter_company_delay_seconds=config.request_interval_seconds,
    )
    result = collector.collect_batch(
        companies,
        start_at=start_at,
        end_at=end_at,
        limit_per_company=limit,
    )
    result = replace(
        result,
        metadata={
            **result.metadata,
            "universe": {
                "name": universe.universe_name,
                "mode": universe.universe_mode,
                "as_of": universe.as_of,
                "source_url": universe.source_url,
            },
            "requested_window": {
                "start_at_utc": start_at.isoformat().replace("+00:00", "Z"),
                "end_at_utc": end_at.isoformat().replace("+00:00", "Z"),
            },
        },
    )
    return result


def run(args: argparse.Namespace) -> tuple[dict, int]:
    run_id = None
    if args.persist:
        repository = NewsRepository(args.db_name)
        with repository.collection_lock():
            result = collect_result(args)
            run_id = repository.save_collection(result)
    else:
        result = collect_result(args)

    payload = result.to_dict()
    if run_id is not None:
        payload["persistence"] = {
            "status": "saved",
            "run_id": str(run_id),
        }
    return payload, _exit_code(result.status)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        payload, exit_code = run(args)
    except CollectionAlreadyRunningError as exc:
        payload = {
            "collection_status": "failed",
            "collection_outcome": "already_running",
            "failure_reason": str(exc),
            "relevant_article_count": None,
        }
        exit_code = EXIT_ALREADY_RUNNING
    except DatabaseError as exc:
        payload = {
            "collection_status": "failed",
            "collection_outcome": "persistence_error",
            "failure_reason": f"{type(exc).__name__}: {exc}",
            "relevant_article_count": None,
        }
        exit_code = EXIT_PERSISTENCE_ERROR
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        payload = {
            "collection_status": "failed",
            "collection_outcome": CollectionStatus.INVALID_CONFIG.value,
            "failure_reason": f"{type(exc).__name__}: {exc}",
            "relevant_article_count": None,
        }
        exit_code = EXIT_INVALID_CONFIG

    serialized = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(serialized + "\n", encoding="utf-8")
    else:
        print(serialized)
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
