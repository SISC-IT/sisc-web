from __future__ import annotations

import json
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path
from typing import Any, Literal

from AI.modules.data_collector.components.sec_edgar_repository import (
    SecFilingRepository,
)


QuerySource = Literal["db", "file"]


class SecFilingFileQuery:
    """파일 모드로 저장한 filing.json을 DB 없이 조회합니다."""

    def __init__(self, data_dir: str | Path):
        self.data_dir = Path(data_dir)

    def list_filings(
        self,
        *,
        ticker: str | None = None,
        form_type: str | None = None,
        event_type: str | None = None,
        start_date: str | None = None,
        end_date: str | None = None,
        signals_only: bool = False,
        signal_code: str | None = None,
        limit: int = 50,
        offset: int = 0,
    ) -> list[dict[str, Any]]:
        _validate_pagination(limit, offset)
        _validate_signal_code(signal_code)
        summaries: list[dict[str, Any]] = []

        for payload in self._iter_payloads():
            filing = payload.get("filing", {})
            transactions = payload.get("insider_transactions", [])
            signal_transactions = [
                transaction
                for transaction in transactions
                if transaction.get("is_signal")
            ]
            if ticker and str(filing.get("ticker") or "").upper() != ticker.upper():
                continue
            if (
                form_type
                and str(filing.get("form_type") or "").upper() != form_type.upper()
            ):
                continue
            if (
                event_type
                and str(filing.get("event_type") or "").upper() != event_type.upper()
            ):
                continue

            filing_date = str(filing.get("filing_date") or "")
            if start_date and filing_date < start_date:
                continue
            if end_date and filing_date > end_date:
                continue
            if signals_only and not signal_transactions:
                continue
            if signal_code and not any(
                transaction.get("transaction_code") == signal_code
                for transaction in signal_transactions
            ):
                continue

            summary = dict(filing)
            summary["document_count"] = len(payload.get("documents", []))
            summary["signal_count"] = len(signal_transactions)
            summaries.append(summary)

        summaries.sort(
            key=lambda item: (
                str(item.get("accepted_at") or ""),
                str(item.get("filing_date") or ""),
            ),
            reverse=True,
        )
        return summaries[offset : offset + limit]

    def get_filing(
        self,
        accession_number: str,
        *,
        include_content: bool = False,
    ) -> dict[str, Any] | None:
        payload = self._load_by_accession(accession_number)
        if payload is None:
            return None
        if include_content:
            return payload

        for document in payload.get("documents", []):
            document.pop("content_text", None)
        return payload

    def get_document(
        self,
        accession_number: str,
        *,
        document_id: int | None = None,
        document_name: str | None = None,
    ) -> dict[str, Any] | None:
        if (document_id is None) == (document_name is None):
            raise ValueError("document_id와 document_name 중 하나만 지정해야 합니다.")
        payload = self._load_by_accession(accession_number)
        if payload is None:
            return None

        for document in payload.get("documents", []):
            if document_id is not None and document.get("sequence") == document_id:
                return document
            if (
                document_name is not None
                and document.get("document_name") == document_name
            ):
                return document
        return None

    def _load_by_accession(self, accession_number: str) -> dict[str, Any] | None:
        compact = accession_number.replace("-", "")
        candidates = list(self.data_dir.glob(f"*/{compact}/filing.json"))
        if not candidates:
            return None
        return json.loads(candidates[0].read_text(encoding="utf-8"))

    def _iter_payloads(self):
        if not self.data_dir.exists():
            return
        for path in self.data_dir.glob("*/*/filing.json"):
            try:
                yield json.loads(path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                continue


def create_sec_filing_query(
    source: QuerySource,
    *,
    db_name: str = "db",
    data_dir: str | Path,
):
    """CLI와 다른 Python 모듈이 동일한 조회 구현을 선택하도록 합니다."""

    if source == "db":
        return SecFilingRepository(db_name)
    if source == "file":
        return SecFilingFileQuery(data_dir)
    raise ValueError(f"지원하지 않는 조회 소스입니다: {source}")


def to_json_text(value: Any, *, pretty: bool = True) -> str:
    """DB 날짜·Decimal·Path를 포함한 조회 결과를 JSON 문자열로 변환합니다."""

    return json.dumps(
        value,
        ensure_ascii=False,
        indent=2 if pretty else None,
        default=_json_default,
    )


def _json_default(value: Any):
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    if isinstance(value, Decimal):
        return str(value)
    if isinstance(value, Path):
        return str(value)
    raise TypeError(f"JSON으로 변환할 수 없는 타입입니다: {type(value).__name__}")


def _validate_pagination(limit: int, offset: int) -> None:
    if not 1 <= limit <= 200:
        raise ValueError("limit은 1 이상 200 이하여야 합니다.")
    if offset < 0:
        raise ValueError("offset은 0 이상이어야 합니다.")


def _validate_signal_code(signal_code: str | None) -> None:
    if signal_code is not None and signal_code not in {"P", "S"}:
        raise ValueError("signal_code는 P 또는 S만 허용합니다.")
