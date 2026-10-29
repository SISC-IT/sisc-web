from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Any, Iterable
from urllib.parse import parse_qs, urljoin, urlparse
from zoneinfo import ZoneInfo

from bs4 import BeautifulSoup

from AI.modules.data_collector.components.sec_edgar_models import (
    SecDocument,
    SecFiling,
    SecInsiderTransaction,
)


SEC_ARCHIVES_BASE_URL = "https://www.sec.gov"
SEC_EASTERN = ZoneInfo("America/New_York")


def normalize_cik(value: str | int) -> str:
    """SEC API 경로에서 사용하는 10자리 CIK로 맞춥니다."""

    digits = re.sub(r"\D", "", str(value))
    if not digits:
        raise ValueError(f"유효하지 않은 CIK입니다: {value}")
    return digits.zfill(10)


def parse_iso_date(value: Any) -> date | None:
    if value in (None, ""):
        return None
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    text = str(value).strip()
    for fmt in ("%Y-%m-%d", "%Y%m%d"):
        try:
            return datetime.strptime(text, fmt).date()
        except ValueError:
            continue
    return None


def parse_sec_accepted_at(value: Any) -> datetime | None:
    """EDGAR acceptance 시간을 미국 동부 시간대가 포함된 datetime으로 변환합니다."""

    if value in (None, ""):
        return None
    if isinstance(value, datetime):
        return value if value.tzinfo else value.replace(tzinfo=SEC_EASTERN)

    text = str(value).strip()
    if text.endswith("Z"):
        try:
            return datetime.fromisoformat(text.replace("Z", "+00:00"))
        except ValueError:
            return None

    for fmt in ("%Y%m%d%H%M%S", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S"):
        try:
            return datetime.strptime(text, fmt).replace(tzinfo=SEC_EASTERN)
        except ValueError:
            continue
    try:
        parsed = datetime.fromisoformat(text)
        return parsed if parsed.tzinfo else parsed.replace(tzinfo=SEC_EASTERN)
    except ValueError:
        return None


def flatten_submission_rows(payload: dict[str, Any]) -> list[dict[str, Any]]:
    """Submissions API의 컬럼 배열을 공시별 dict 목록으로 변환합니다."""

    recent = payload.get("filings", {}).get("recent", payload)
    if not isinstance(recent, dict):
        return []
    columns = {key: value for key, value in recent.items() if isinstance(value, list)}
    if not columns:
        return []

    row_count = max(len(values) for values in columns.values())
    return [
        {
            key: values[index] if index < len(values) else None
            for key, values in columns.items()
        }
        for index in range(row_count)
    ]


def split_item_codes(value: Any) -> tuple[str, ...]:
    if value in (None, ""):
        return ()
    return tuple(
        item
        for item in (part.strip() for part in re.split(r"[,;]", str(value)))
        if item
    )


def classify_event_type(form_type: str, item_codes: Iterable[str]) -> str:
    normalized_form = form_type.upper()
    items = set(item_codes)
    if normalized_form in {"4", "4/A"}:
        return "FORM_4"
    if "2.02" in items:
        return "EARNINGS_8K_2_02"
    if "5.02" in items:
        return "EXECUTIVE_CHANGE_8K_5_02"
    return "OTHER"


def build_filing_url(cik: str, accession_number: str) -> str:
    cik_compact = str(int(normalize_cik(cik)))
    accession_compact = accession_number.replace("-", "")
    return (
        f"{SEC_ARCHIVES_BASE_URL}/Archives/edgar/data/"
        f"{cik_compact}/{accession_compact}/{accession_number}-index.html"
    )


def filing_from_submission_row(
    row: dict[str, Any],
    *,
    cik: str,
    ticker: str | None,
    company_name: str,
) -> SecFiling:
    accession_number = str(row.get("accessionNumber") or "").strip()
    filing_date = parse_iso_date(row.get("filingDate"))
    primary_document = str(row.get("primaryDocument") or "").strip()
    if not accession_number or filing_date is None or not primary_document:
        raise ValueError("공시 필수 메타데이터가 누락됐습니다.")

    form_type = str(row.get("form") or "").strip()
    item_codes = split_item_codes(row.get("items"))
    return SecFiling(
        accession_number=accession_number,
        cik=normalize_cik(cik),
        ticker=ticker.upper() if ticker else None,
        company_name=company_name,
        form_type=form_type,
        filing_date=filing_date,
        accepted_at=parse_sec_accepted_at(
            row.get("acceptanceDateTime") or row.get("acceptedAt")
        ),
        report_date=parse_iso_date(row.get("reportDate")),
        primary_document=primary_document,
        primary_doc_description=_nullable_text(row.get("primaryDocDescription")),
        item_codes=item_codes,
        event_type=classify_event_type(form_type, item_codes),
        sec_url=build_filing_url(cik, accession_number),
        metadata={
            key: value
            for key, value in row.items()
            if key
            not in {
                "accessionNumber",
                "filingDate",
                "acceptanceDateTime",
                "acceptedAt",
                "reportDate",
                "form",
                "items",
                "primaryDocument",
                "primaryDocDescription",
            }
        },
    )


def parse_filing_index(
    html: str,
    *,
    index_url: str,
    primary_document: str,
) -> list[SecDocument]:
    """공시 인덱스 표에서 원문과 Exhibit 목록을 추출합니다."""

    soup = BeautifulSoup(html, "html.parser")
    documents: list[SecDocument] = []
    tables = soup.select("table.tableFile, table[summary*='Document Format Files']")
    primary_name = primary_document.replace("\\", "/").rsplit("/", 1)[-1]

    for table in tables:
        for row in table.select("tr"):
            cells = row.find_all("td")
            if len(cells) < 4:
                continue
            link = cells[2].find("a", href=True)
            if link is None:
                continue

            sequence_text = cells[0].get_text(" ", strip=True)
            try:
                sequence = int(sequence_text)
            except ValueError:
                sequence = len(documents) + 1

            description = _nullable_text(cells[1].get_text(" ", strip=True))
            document_name = link.get_text(" ", strip=True) or link["href"].rsplit("/", 1)[-1]
            document_type = cells[3].get_text(" ", strip=True)
            source_url = _raw_document_url(index_url, link["href"])
            # Form 4의 primaryDocument는 xslF345X*/ownership.xml처럼 XSL 변환
            # 경로를 포함하지만, 인덱스에는 변환본과 원본 XML이 함께 노출됩니다.
            # 경로 전체가 아닌 파일명을 비교하되 XSL 경로는 원본으로 선택하지 않습니다.
            is_primary = (
                document_name.lower() == primary_name.lower()
                and not _is_xsl_transformed_url(source_url)
            )
            is_exhibit = document_type.upper().startswith("EX-")

            documents.append(
                SecDocument(
                    sequence=sequence,
                    document_name=document_name,
                    document_type=document_type,
                    description=description,
                    source_url=source_url,
                    is_primary=is_primary,
                    is_exhibit=is_exhibit,
                )
            )

    if not any(document.is_primary for document in documents):
        base_url = index_url.rsplit("/", 1)[0] + "/"
        documents.insert(
            0,
            SecDocument(
                sequence=1,
                document_name=primary_name,
                document_type="PRIMARY",
                description="Primary document",
                source_url=urljoin(base_url, primary_name),
                is_primary=True,
            ),
        )
    return _deduplicate_documents(documents)


def document_to_text(content: bytes, content_type: str | None, document_name: str) -> str:
    """HTML/XML/TXT 문서를 검색과 AI 입력에 적합한 평문으로 변환합니다."""

    text = content.decode("utf-8", errors="replace")
    normalized_type = (content_type or "").lower()
    suffix = document_name.lower().rsplit(".", 1)[-1] if "." in document_name else ""
    if "html" in normalized_type or suffix in {"htm", "html"}:
        soup = BeautifulSoup(text, "html.parser")
        for tag in soup(["script", "style", "noscript"]):
            tag.decompose()
        return "\n".join(
            line for line in (_collapse_space(value) for value in soup.stripped_strings) if line
        )
    return text.strip()


def parse_form4_xml(xml_content: bytes | str) -> list[SecInsiderTransaction]:
    """Form 4 XML의 비파생·파생 거래를 모두 보존하고 P/S 신호를 구분합니다."""

    root = ET.fromstring(xml_content)
    owner = root.find(".//{*}reportingOwner")
    owner_cik = _xml_text(owner, ".//{*}rptOwnerCik")
    owner_name = _xml_text(owner, ".//{*}rptOwnerName")
    relationship = owner.find(".//{*}reportingOwnerRelationship") if owner is not None else None

    common = {
        "reporting_owner_cik": normalize_cik(owner_cik) if owner_cik else None,
        "reporting_owner_name": owner_name,
        "is_director": _xml_bool(relationship, ".//{*}isDirector"),
        "is_officer": _xml_bool(relationship, ".//{*}isOfficer"),
        "is_ten_percent_owner": _xml_bool(relationship, ".//{*}isTenPercentOwner"),
        "is_other": _xml_bool(relationship, ".//{*}isOther"),
        "officer_title": _xml_text(relationship, ".//{*}officerTitle"),
    }

    transactions: list[SecInsiderTransaction] = []
    groups = (
        ("NON_DERIVATIVE", False, root.findall(".//{*}nonDerivativeTransaction")),
        ("DERIVATIVE", True, root.findall(".//{*}derivativeTransaction")),
    )
    for category, is_derivative, nodes in groups:
        for node in nodes:
            code = _xml_text(node, ".//{*}transactionCoding/{*}transactionCode")
            transaction = SecInsiderTransaction(
                transaction_index=len(transactions),
                security_category=category,
                security_title=_xml_text(node, "./{*}securityTitle/{*}value"),
                transaction_date=parse_iso_date(
                    _xml_text(node, "./{*}transactionDate/{*}value")
                ),
                transaction_code=code,
                acquired_disposed_code=_xml_text(
                    node,
                    "./{*}transactionAmounts/{*}transactionAcquiredDisposedCode/{*}value",
                ),
                shares=_xml_decimal(
                    node,
                    "./{*}transactionAmounts/{*}transactionShares/{*}value",
                ),
                price_per_share=_xml_decimal(
                    node,
                    "./{*}transactionAmounts/{*}transactionPricePerShare/{*}value",
                ),
                shares_owned_after=_xml_decimal(
                    node,
                    "./{*}postTransactionAmounts/{*}sharesOwnedFollowingTransaction/{*}value",
                ),
                ownership_form=_xml_text(
                    node,
                    "./{*}ownershipNature/{*}directOrIndirectOwnership/{*}value",
                ),
                is_derivative=is_derivative,
                is_signal=not is_derivative and code in {"P", "S"},
                footnote_ids=tuple(
                    element.attrib["id"]
                    for element in node.findall(".//{*}footnoteId")
                    if element.attrib.get("id")
                ),
                **common,
            )
            transactions.append(transaction)
    return transactions


def _raw_document_url(index_url: str, href: str) -> str:
    absolute = urljoin(index_url, href)
    parsed = urlparse(absolute)
    query = parse_qs(parsed.query)
    if parsed.path.endswith("/ixviewer/doc/action") and query.get("doc"):
        return urljoin(SEC_ARCHIVES_BASE_URL, query["doc"][0])
    if parsed.path.startswith("/ix"):
        doc_values = query.get("doc")
        if doc_values:
            return urljoin(SEC_ARCHIVES_BASE_URL, doc_values[0])
    return absolute


def _deduplicate_documents(documents: list[SecDocument]) -> list[SecDocument]:
    result: list[SecDocument] = []
    positions: dict[str, int] = {}
    for document in documents:
        key = document.document_name.lower()
        position = positions.get(key)
        if position is not None:
            if document.is_primary and not result[position].is_primary:
                result[position] = document
            continue
        positions[key] = len(result)
        result.append(document)
    return result


def _is_xsl_transformed_url(url: str) -> bool:
    return any(
        segment.lower().startswith("xsl")
        for segment in urlparse(url).path.split("/")
        if segment
    )


def _nullable_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _collapse_space(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def _xml_text(node: ET.Element | None, path: str) -> str | None:
    if node is None:
        return None
    found = node.find(path)
    if found is None or found.text is None:
        return None
    return found.text.strip() or None


def _xml_bool(node: ET.Element | None, path: str) -> bool:
    return (_xml_text(node, path) or "").lower() in {"1", "true", "yes"}


def _xml_decimal(node: ET.Element, path: str) -> Decimal | None:
    value = _xml_text(node, path)
    if value is None:
        return None
    try:
        return Decimal(value.replace(",", ""))
    except InvalidOperation:
        return None
