from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class SecCompany:
    """SEC CIK와 거래소 티커를 연결한 회사 정보입니다."""

    cik: str
    ticker: str | None
    company_name: str
    exchange: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class SecFiling:
    """Submissions API에서 정규화한 공시 메타데이터입니다."""

    accession_number: str
    cik: str
    ticker: str | None
    company_name: str
    form_type: str
    filing_date: date
    accepted_at: datetime | None
    report_date: date | None
    primary_document: str
    primary_doc_description: str | None
    item_codes: tuple[str, ...]
    event_type: str
    sec_url: str
    metadata: dict[str, Any] = field(default_factory=dict)

    @property
    def accession_compact(self) -> str:
        return self.accession_number.replace("-", "")

    @property
    def cik_compact(self) -> str:
        return str(int(self.cik))

    def to_dict(self) -> dict[str, Any]:
        payload = asdict(self)
        payload["filing_date"] = self.filing_date.isoformat()
        payload["accepted_at"] = self.accepted_at.isoformat() if self.accepted_at else None
        payload["report_date"] = self.report_date.isoformat() if self.report_date else None
        payload["item_codes"] = list(self.item_codes)
        return payload


@dataclass(frozen=True)
class SecDocument:
    """공시 인덱스에 포함된 원문 또는 첨부 문서입니다."""

    sequence: int
    document_name: str
    document_type: str
    description: str | None
    source_url: str
    is_primary: bool = False
    is_exhibit: bool = False
    content_type: str | None = None
    content_text: str | None = None
    content_hash: str | None = None
    local_path: Path | None = None

    def to_dict(self) -> dict[str, Any]:
        payload = asdict(self)
        payload["local_path"] = str(self.local_path) if self.local_path else None
        return payload


@dataclass(frozen=True)
class SecInsiderTransaction:
    """Form 4의 비파생·파생 거래 한 행을 정규화한 결과입니다."""

    transaction_index: int
    security_category: str
    reporting_owner_cik: str | None
    reporting_owner_name: str | None
    is_director: bool
    is_officer: bool
    is_ten_percent_owner: bool
    is_other: bool
    officer_title: str | None
    security_title: str | None
    transaction_date: date | None
    transaction_code: str | None
    acquired_disposed_code: str | None
    shares: Decimal | None
    price_per_share: Decimal | None
    shares_owned_after: Decimal | None
    ownership_form: str | None
    is_derivative: bool
    is_signal: bool
    footnote_ids: tuple[str, ...] = ()

    @property
    def transaction_type(self) -> str:
        if self.transaction_code == "P":
            return "PURCHASE"
        if self.transaction_code == "S":
            return "SALE"
        return "OTHER"

    def to_dict(self) -> dict[str, Any]:
        payload = asdict(self)
        payload["transaction_date"] = (
            self.transaction_date.isoformat() if self.transaction_date else None
        )
        for key in ("shares", "price_per_share", "shares_owned_after"):
            value = payload[key]
            payload[key] = str(value) if value is not None else None
        payload["footnote_ids"] = list(self.footnote_ids)
        payload["transaction_type"] = self.transaction_type
        return payload


@dataclass(frozen=True)
class CollectedSecFiling:
    """공시 메타데이터와 실제 문서·Form 4 거래를 묶은 저장 단위입니다."""

    filing: SecFiling
    documents: tuple[SecDocument, ...]
    insider_transactions: tuple[SecInsiderTransaction, ...] = ()

    def to_dict(self) -> dict[str, Any]:
        return {
            "filing": self.filing.to_dict(),
            "documents": [document.to_dict() for document in self.documents],
            "insider_transactions": [
                transaction.to_dict() for transaction in self.insider_transactions
            ],
        }
