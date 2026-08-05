from __future__ import annotations

import hashlib
import json
import logging
import os
from dataclasses import dataclass, replace
from datetime import date, datetime
from pathlib import Path
from typing import Any, Iterable, Literal

from AI.modules.data_collector.components.sec_edgar_client import SecEdgarClient
from AI.modules.data_collector.components.sec_edgar_models import (
    CollectedSecFiling,
    SecCompany,
    SecDocument,
)
from AI.modules.data_collector.components.sec_edgar_parser import (
    document_to_text,
    filing_from_submission_row,
    flatten_submission_rows,
    normalize_cik,
    parse_filing_index,
    parse_form4_xml,
    parse_iso_date,
)
from AI.modules.data_collector.components.sec_edgar_repository import SecFilingRepository


Storage = Literal["file", "db", "both"]
PROJECT_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_CONFIG_PATH = PROJECT_ROOT / "AI/modules/data_collector/config/sec_edgar.json"

COMPANY_TICKERS_URL = "https://www.sec.gov/files/company_tickers.json"
SUBMISSIONS_URL = "https://data.sec.gov/submissions/CIK{cik}.json"
SUBMISSIONS_FILE_URL = "https://data.sec.gov/submissions/{name}"


@dataclass(frozen=True)
class SecEdgarCollectorConfig:
    """SEC EDGAR 수집 실행 설정입니다."""

    user_agent: str = ""
    forms: tuple[str, ...] = ("8-K", "8-K/A", "4", "4/A")
    item_codes: tuple[str, ...] = ("2.02", "5.02")
    start_date: str = "2021-01-01"
    end_date: str | None = None
    storage: Storage = "both"
    data_dir: Path = PROJECT_ROOT / "AI/modules/data_collector/storage/sec_edgar"
    cache_dir: Path = PROJECT_ROOT / "AI/modules/data_collector/cache/sec_edgar"
    log_dir: Path = PROJECT_ROOT / "AI/modules/data_collector/logs"
    db_name: str = "db"
    max_requests_per_second: float = 8.0
    timeout_seconds: float = 20.0
    max_retries: int = 3
    cache_ttl_seconds: int = 86_400
    include_historical_files: bool = True
    include_all_exhibits: bool = False

    @classmethod
    def from_file(
        cls, path: str | Path = DEFAULT_CONFIG_PATH
    ) -> "SecEdgarCollectorConfig":
        config_path = _resolve_project_path(path)
        raw = json.loads(config_path.read_text(encoding="utf-8"))
        return cls(
            user_agent=os.getenv("SEC_USER_AGENT", raw.get("user_agent", "")).strip(),
            forms=tuple(raw.get("forms", cls.forms)),
            item_codes=tuple(raw.get("item_codes", cls.item_codes)),
            start_date=raw.get("start_date", cls.start_date),
            end_date=raw.get("end_date"),
            storage=raw.get("storage", cls.storage),
            data_dir=_resolve_project_path(raw.get("data_dir", cls.data_dir)),
            cache_dir=_resolve_project_path(raw.get("cache_dir", cls.cache_dir)),
            log_dir=_resolve_project_path(raw.get("log_dir", cls.log_dir)),
            db_name=raw.get("db_name", cls.db_name),
            max_requests_per_second=float(
                raw.get("max_requests_per_second", cls.max_requests_per_second)
            ),
            timeout_seconds=float(raw.get("timeout_seconds", cls.timeout_seconds)),
            max_retries=int(raw.get("max_retries", cls.max_retries)),
            cache_ttl_seconds=int(
                raw.get("cache_ttl_seconds", cls.cache_ttl_seconds)
            ),
            include_historical_files=bool(
                raw.get("include_historical_files", cls.include_historical_files)
            ),
            include_all_exhibits=bool(
                raw.get("include_all_exhibits", cls.include_all_exhibits)
            ),
        )

    def with_overrides(self, **kwargs) -> "SecEdgarCollectorConfig":
        values = {
            key: value
            for key, value in kwargs.items()
            if value is not None
        }
        for key in ("data_dir", "cache_dir", "log_dir"):
            if key in values:
                values[key] = _resolve_project_path(values[key])
        for key in ("forms", "item_codes"):
            if key in values:
                values[key] = tuple(values[key])
        return replace(self, **values)


class SecEdgarDataCollector:
    """SEC 공시를 수집해 원문 파일과 PostgreSQL에 멱등 저장합니다."""

    def __init__(
        self,
        config: SecEdgarCollectorConfig,
        *,
        client: SecEdgarClient | None = None,
        repository: SecFilingRepository | None = None,
    ):
        self.config = config
        self.client = client or SecEdgarClient(
            config.user_agent,
            cache_dir=config.cache_dir,
            max_requests_per_second=config.max_requests_per_second,
            timeout_seconds=config.timeout_seconds,
            max_retries=config.max_retries,
            cache_ttl_seconds=config.cache_ttl_seconds,
        )
        self.repository = repository or SecFilingRepository(config.db_name)
        self.logger = _build_logger(config.log_dir)
        config.data_dir.mkdir(parents=True, exist_ok=True)

    def close(self) -> None:
        """Windows에서도 로그 파일 잠금이 남지 않도록 handler를 명시적으로 닫습니다."""

        for handler in list(self.logger.handlers):
            handler.close()
            self.logger.removeHandler(handler)

    def __enter__(self) -> "SecEdgarDataCollector":
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.close()

    def fetch_company_tickers(self) -> list[SecCompany]:
        payload = self.client.get_json(COMPANY_TICKERS_URL)
        companies: list[SecCompany] = []
        rows = payload.values() if isinstance(payload, dict) else payload
        for row in rows:
            ticker = str(row.get("ticker") or "").strip().upper()
            if not ticker:
                continue
            companies.append(
                SecCompany(
                    cik=normalize_cik(row["cik_str"]),
                    ticker=ticker,
                    company_name=str(row.get("title") or ticker).strip(),
                    exchange=_nullable_text(row.get("exchange")),
                )
            )
        return companies

    def collect(
        self,
        *,
        tickers: Iterable[str] | None = None,
        ciks: Iterable[str] | None = None,
        limit: int | None = None,
    ) -> dict[str, int]:
        requested_tickers = list(tickers or ())
        requested_ciks = list(ciks or ())
        companies = self.fetch_company_tickers() if requested_tickers else []
        by_ticker = {
            company.ticker: company for company in companies if company.ticker
        }
        by_cik = {company.cik: company for company in companies}

        selected: list[SecCompany] = []
        for ticker in requested_tickers:
            normalized = ticker.upper()
            if normalized not in by_ticker:
                raise ValueError(f"SEC CIK 매핑에서 티커를 찾지 못했습니다: {ticker}")
            selected.append(by_ticker[normalized])
        for cik in requested_ciks:
            normalized = normalize_cik(cik)
            selected.append(
                by_cik.get(
                    normalized,
                    SecCompany(
                        cik=normalized,
                        ticker=None,
                        company_name=f"CIK {normalized}",
                    ),
                )
            )
        if not selected:
            raise ValueError("--tickers 또는 --ciks 중 하나 이상을 지정해야 합니다.")

        selected = list({company.cik: company for company in selected}.values())
        if limit is not None:
            selected = selected[:limit]

        if self.config.storage in {"db", "both"}:
            self.repository.upsert_companies(selected)

        stats = {
            "companies": len(selected),
            "filings": 0,
            "documents": 0,
            "transactions": 0,
            "failed": 0,
        }
        for company in selected:
            try:
                for filing in self._iter_company_filings(company):
                    try:
                        collected = self.collect_filing(filing)
                        self._save(collected)
                        stats["filings"] += 1
                        stats["documents"] += len(collected.documents)
                        stats["transactions"] += len(collected.insider_transactions)
                    except Exception:
                        stats["failed"] += 1
                        self.logger.exception(
                            "공시 수집에 실패했습니다: %s", filing.accession_number
                        )
            except Exception:
                stats["failed"] += 1
                self.logger.exception(
                    "회사 공시 목록 수집에 실패했습니다: %s",
                    company.ticker or company.cik,
                )
        self.logger.info("SEC EDGAR 수집을 완료했습니다: %s", stats)
        return stats

    def collect_filing(self, filing) -> CollectedSecFiling:
        index_response = self.client.get(filing.sec_url, immutable=True)
        indexed_documents = parse_filing_index(
            index_response.text,
            index_url=filing.sec_url,
            primary_document=filing.primary_document,
        )
        targets = [
            document
            for document in indexed_documents
            if self._should_download_document(filing, document)
        ]

        documents: list[SecDocument] = []
        transactions = []
        for document in targets:
            response = self.client.get(document.source_url, immutable=True)
            content_type = response.headers.get("content-type")
            local_path = self._write_raw_document(
                filing.cik,
                filing.accession_number,
                document.document_name,
                response.content,
            )
            enriched = replace(
                document,
                content_type=content_type,
                content_text=document_to_text(
                    response.content, content_type, document.document_name
                ),
                content_hash=hashlib.sha256(response.content).hexdigest(),
                local_path=local_path,
            )
            documents.append(enriched)
            if (
                filing.form_type.upper() in {"4", "4/A"}
                and document.is_primary
            ):
                transactions = parse_form4_xml(response.content)

        return CollectedSecFiling(
            filing=filing,
            documents=tuple(documents),
            insider_transactions=tuple(transactions),
        )

    def _iter_company_filings(self, company: SecCompany):
        payload = self.client.get_json(SUBMISSIONS_URL.format(cik=company.cik))
        company_name = str(payload.get("name") or company.company_name)
        rows = flatten_submission_rows(payload)

        if self.config.include_historical_files:
            for file_info in payload.get("filings", {}).get("files", []):
                name = file_info.get("name")
                if not name or not self._historical_file_may_overlap(file_info):
                    continue
                historical = self.client.get_json(
                    SUBMISSIONS_FILE_URL.format(name=name),
                    immutable=True,
                )
                rows.extend(flatten_submission_rows(historical))

        seen: set[str] = set()
        for row in rows:
            if not self._row_is_target(row):
                continue
            accession = str(row.get("accessionNumber") or "")
            if not accession or accession in seen:
                continue
            seen.add(accession)
            yield filing_from_submission_row(
                row,
                cik=company.cik,
                ticker=company.ticker,
                company_name=company_name,
            )

    def _row_is_target(self, row: dict[str, Any]) -> bool:
        form = str(row.get("form") or "").upper()
        if form not in {value.upper() for value in self.config.forms}:
            return False
        filing_date = parse_iso_date(row.get("filingDate"))
        if filing_date is None:
            return False
        start = parse_iso_date(self.config.start_date)
        end = parse_iso_date(self.config.end_date) or date.today()
        if start and not start <= filing_date <= end:
            return False
        if form in {"4", "4/A"}:
            return True
        items = {
            item.strip()
            for item in str(row.get("items") or "").replace(";", ",").split(",")
            if item.strip()
        }
        return bool(items.intersection(self.config.item_codes))

    def _historical_file_may_overlap(self, file_info: dict[str, Any]) -> bool:
        start = parse_iso_date(self.config.start_date)
        end = parse_iso_date(self.config.end_date) or date.today()
        file_from = parse_iso_date(file_info.get("filingFrom"))
        file_to = parse_iso_date(file_info.get("filingTo"))
        if not file_from or not file_to or not start:
            return True
        return file_from <= end and file_to >= start

    def _should_download_document(self, filing, document: SecDocument) -> bool:
        if document.is_primary:
            return True
        if filing.form_type.upper() in {"4", "4/A"}:
            return False
        if self.config.include_all_exhibits:
            return document.is_exhibit
        # 임원 변경 공시는 고용계약(EX-10)과 보도자료(EX-99)가 함께 붙을 수 있어
        # 5.02가 포함된 경우 모든 Exhibit를 보존합니다.
        if "5.02" in filing.item_codes:
            return document.is_exhibit
        return document.document_type.upper().startswith("EX-99")

    def _write_raw_document(
        self,
        cik: str,
        accession_number: str,
        document_name: str,
        content: bytes,
    ) -> Path:
        directory = (
            self.config.data_dir
            / normalize_cik(cik)
            / accession_number.replace("-", "")
        )
        directory.mkdir(parents=True, exist_ok=True)
        path = directory / Path(document_name).name
        path.write_bytes(content)
        return path

    def _save(self, collected: CollectedSecFiling) -> None:
        if self.config.storage in {"file", "both"}:
            directory = (
                self.config.data_dir
                / collected.filing.cik
                / collected.filing.accession_compact
            )
            directory.mkdir(parents=True, exist_ok=True)
            (directory / "filing.json").write_text(
                json.dumps(collected.to_dict(), ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
        if self.config.storage in {"db", "both"}:
            self.repository.save(collected)


def _resolve_project_path(path: str | Path) -> Path:
    resolved = Path(path)
    return resolved if resolved.is_absolute() else PROJECT_ROOT / resolved


def _nullable_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _build_logger(log_dir: Path) -> logging.Logger:
    log_dir.mkdir(parents=True, exist_ok=True)
    logger = logging.getLogger("sec_edgar_data")
    logger.setLevel(logging.INFO)
    for handler in list(logger.handlers):
        handler.close()
        logger.removeHandler(handler)

    formatter = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    file_handler = logging.FileHandler(
        log_dir / f"sec_edgar_{datetime.now().strftime('%Y%m%d')}.log",
        encoding="utf-8",
    )
    file_handler.setFormatter(formatter)
    logger.addHandler(file_handler)

    stream_handler = logging.StreamHandler()
    stream_handler.setFormatter(formatter)
    logger.addHandler(stream_handler)
    return logger
