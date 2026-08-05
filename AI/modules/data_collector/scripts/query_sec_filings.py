from __future__ import annotations

import argparse
import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.append(str(PROJECT_ROOT))

from AI.modules.data_collector.components.sec_edgar_data import (
    DEFAULT_CONFIG_PATH,
    SecEdgarCollectorConfig,
)
from AI.modules.data_collector.components.sec_edgar_query import (
    create_sec_filing_query,
    to_json_text,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Python에서 SEC 공시·원문·Form 4 거래를 조회합니다."
    )
    parser.add_argument(
        "--config",
        default=str(DEFAULT_CONFIG_PATH),
        help="SEC 수집기 JSON 설정 파일",
    )
    parser.add_argument(
        "--source",
        choices=["db", "file"],
        default="db",
        help="조회 소스(기본값: db)",
    )
    parser.add_argument("--db", dest="db_name", help="DB 환경변수 접두사")
    parser.add_argument("--data-dir", help="file 조회 시 SEC 저장 경로")

    parser.add_argument("--ticker", help="티커 필터")
    parser.add_argument(
        "--form",
        dest="form_type",
        choices=["8-K", "8-K/A", "4", "4/A"],
        help="공시 양식 필터",
    )
    parser.add_argument("--event-type", help="정규화 이벤트 유형 필터")
    parser.add_argument("--start", dest="start_date", help="공시 시작일")
    parser.add_argument("--end", dest="end_date", help="공시 종료일")
    parser.add_argument(
        "--signals-only",
        action="store_true",
        help="Form 4 P·S 신호가 있는 공시만 조회",
    )
    parser.add_argument(
        "--signal-code",
        choices=["P", "S"],
        help="P(매수) 또는 S(매도) 신호 필터",
    )
    parser.add_argument("--limit", type=int, default=50, help="최대 조회 건수")
    parser.add_argument("--offset", type=int, default=0, help="조회 시작 위치")

    parser.add_argument(
        "--accession",
        help="단일 공시 상세 조회용 accession number",
    )
    document_group = parser.add_mutually_exclusive_group()
    document_group.add_argument(
        "--document-id",
        type=int,
        help="DB 문서 ID 또는 file 문서 sequence",
    )
    document_group.add_argument(
        "--document-name",
        help="공시에 포함된 문서 파일명",
    )
    parser.add_argument(
        "--include-content",
        action="store_true",
        help="상세 조회 결과에 모든 문서 본문 포함",
    )

    parser.add_argument("--output", help="JSON 결과 저장 경로")
    parser.add_argument(
        "--compact",
        action="store_true",
        help="들여쓰기 없는 JSON 출력",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if (args.document_id is not None or args.document_name) and not args.accession:
        raise SystemExit("--document-id/--document-name 조회에는 --accession이 필요합니다.")

    config = SecEdgarCollectorConfig.from_file(args.config)
    query = create_sec_filing_query(
        args.source,
        db_name=args.db_name or config.db_name,
        data_dir=args.data_dir or config.data_dir,
    )

    if args.accession:
        if args.document_id is not None or args.document_name:
            result = query.get_document(
                args.accession,
                document_id=args.document_id,
                document_name=args.document_name,
            )
        else:
            result = query.get_filing(
                args.accession,
                include_content=args.include_content,
            )
        if result is None:
            raise SystemExit("조건에 맞는 SEC 공시 또는 문서를 찾지 못했습니다.")
    else:
        result = query.list_filings(
            ticker=args.ticker,
            form_type=args.form_type,
            event_type=args.event_type,
            start_date=args.start_date,
            end_date=args.end_date,
            signals_only=args.signals_only,
            signal_code=args.signal_code,
            limit=args.limit,
            offset=args.offset,
        )

    output = to_json_text(result, pretty=not args.compact)
    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(output + "\n", encoding="utf-8")
        print(f"SEC 조회 결과를 저장했습니다: {output_path}")
    else:
        print(output)


if __name__ == "__main__":
    main()
