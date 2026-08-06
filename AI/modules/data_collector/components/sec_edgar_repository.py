from __future__ import annotations

from typing import Any, Callable, Iterable

from psycopg2.extras import Json, RealDictCursor, execute_values

from AI.libs.database.connection import get_db_conn
from AI.modules.data_collector.components.sec_edgar_models import (
    CollectedSecFiling,
    SecCompany,
)

class SecFilingRepository:
    """SEC 공시 결과를 PostgreSQL에 멱등 저장합니다."""

    def __init__(
        self,
        db_name: str = "db",
        *,
        connection_factory: Callable = get_db_conn,
    ):
        self.db_name = db_name
        self.connection_factory = connection_factory

    def upsert_companies(self, companies: Iterable[SecCompany]) -> int:
        records = [
            (company.ticker, company.cik, company.company_name, company.exchange)
            for company in companies
            if company.ticker
        ]
        if not records:
            return 0

        query = """
            INSERT INTO public.sec_company_tickers
                (ticker, cik, company_name, exchange)
            VALUES %s
            ON CONFLICT (ticker) DO UPDATE SET
                cik = EXCLUDED.cik,
                company_name = EXCLUDED.company_name,
                exchange = EXCLUDED.exchange,
                updated_at = NOW()
        """
        connection = self.connection_factory(self.db_name)
        try:
            with connection.cursor() as cursor:
                execute_values(cursor, query, records)
            connection.commit()
            return len(records)
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    def save(self, collected: CollectedSecFiling) -> None:
        connection = self.connection_factory(self.db_name)
        try:
            with connection.cursor() as cursor:
                self._upsert_filing(cursor, collected)
                self._upsert_documents(cursor, collected)
                self._upsert_transactions(cursor, collected)
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

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
        """공시 목록을 조건별로 조회하고 최신 접수 순으로 반환합니다."""

        if not 1 <= limit <= 200:
            raise ValueError("limit은 1 이상 200 이하여야 합니다.")
        if offset < 0:
            raise ValueError("offset은 0 이상이어야 합니다.")
        if signal_code is not None and signal_code not in {"P", "S"}:
            raise ValueError("signal_code는 P 또는 S만 허용합니다.")

        conditions = ["1 = 1"]
        params: list[Any] = []
        if ticker:
            conditions.append("UPPER(f.ticker) = UPPER(%s)")
            params.append(ticker.strip())
        if form_type:
            conditions.append("UPPER(f.form_type) = UPPER(%s)")
            params.append(form_type.strip())
        if event_type:
            conditions.append("UPPER(f.event_type) = UPPER(%s)")
            params.append(event_type.strip())
        if start_date:
            conditions.append("f.filing_date >= %s")
            params.append(start_date)
        if end_date:
            conditions.append("f.filing_date <= %s")
            params.append(end_date)
        if signals_only or signal_code:
            signal_condition = ""
            if signal_code:
                signal_condition = " AND t.transaction_code = %s"
                params.append(signal_code)
            conditions.append(
                """
                EXISTS (
                    SELECT 1
                    FROM public.sec_insider_transactions t
                    WHERE t.accession_number = f.accession_number
                      AND t.is_signal = TRUE
                """
                + signal_condition
                + ")"
            )

        params.extend([limit, offset])
        query = f"""
            SELECT
                f.accession_number,
                f.cik,
                f.ticker,
                f.company_name,
                f.form_type,
                f.filing_date,
                f.accepted_at,
                f.report_date,
                f.primary_document,
                f.primary_doc_description,
                f.item_codes,
                f.event_type,
                f.sec_url,
                (
                    SELECT COUNT(*)
                    FROM public.sec_filing_documents d
                    WHERE d.accession_number = f.accession_number
                ) AS document_count,
                (
                    SELECT COUNT(*)
                    FROM public.sec_insider_transactions t
                    WHERE t.accession_number = f.accession_number
                      AND t.is_signal = TRUE
                ) AS signal_count
            FROM public.sec_filings f
            WHERE {" AND ".join(conditions)}
            ORDER BY f.accepted_at DESC NULLS LAST, f.filing_date DESC
            LIMIT %s OFFSET %s
        """
        return self._fetch_all(query, params)

    def get_filing(
        self,
        accession_number: str,
        *,
        include_content: bool = False,
    ) -> dict[str, Any] | None:
        """accession number 기준으로 공시·문서·Form 4 거래를 함께 조회합니다."""

        document_content_column = ", d.content_text" if include_content else ""
        connection = self.connection_factory(self.db_name)
        try:
            with connection.cursor(cursor_factory=RealDictCursor) as cursor:
                cursor.execute(
                    """
                    SELECT
                        accession_number, cik, ticker, company_name, form_type,
                        filing_date, accepted_at, report_date, primary_document,
                        primary_doc_description, item_codes, event_type, sec_url,
                        metadata_json, created_at, updated_at
                    FROM public.sec_filings
                    WHERE accession_number = %s
                    """,
                    (accession_number,),
                )
                filing = cursor.fetchone()
                if filing is None:
                    return None

                cursor.execute(
                    f"""
                    SELECT
                        d.document_id, d.sequence_number, d.document_name,
                        d.document_type, d.description, d.source_url,
                        d.local_path, d.content_type, d.content_hash,
                        d.is_primary, d.is_exhibit
                        {document_content_column}
                    FROM public.sec_filing_documents d
                    WHERE d.accession_number = %s
                    ORDER BY d.sequence_number, d.document_id
                    """,
                    (accession_number,),
                )
                documents = cursor.fetchall()

                cursor.execute(
                    """
                    SELECT
                        transaction_id, transaction_index, security_category,
                        reporting_owner_cik, reporting_owner_name, is_director,
                        is_officer, is_ten_percent_owner, is_other, officer_title,
                        security_title, transaction_date, transaction_code,
                        transaction_type, acquired_disposed_code, shares,
                        price_per_share, shares_owned_after, ownership_form,
                        is_derivative, is_signal, footnote_ids
                    FROM public.sec_insider_transactions
                    WHERE accession_number = %s
                    ORDER BY transaction_index, transaction_id
                    """,
                    (accession_number,),
                )
                transactions = cursor.fetchall()
                return {
                    "filing": dict(filing),
                    "documents": [dict(document) for document in documents],
                    "insider_transactions": [
                        dict(transaction) for transaction in transactions
                    ],
                }
        finally:
            connection.close()

    def get_document(
        self,
        accession_number: str,
        *,
        document_id: int | None = None,
        document_name: str | None = None,
    ) -> dict[str, Any] | None:
        """공시에 속한 문서 하나와 평문 본문을 조회합니다."""

        if (document_id is None) == (document_name is None):
            raise ValueError("document_id와 document_name 중 하나만 지정해야 합니다.")
        if document_id is not None:
            condition = "document_id = %s"
            identifier: Any = document_id
        else:
            condition = "document_name = %s"
            identifier = document_name

        rows = self._fetch_all(
            f"""
            SELECT
                document_id, accession_number, sequence_number, document_name,
                document_type, description, source_url, local_path,
                content_type, content_text, content_hash, is_primary, is_exhibit
            FROM public.sec_filing_documents
            WHERE accession_number = %s AND {condition}
            LIMIT 1
            """,
            [accession_number, identifier],
        )
        return rows[0] if rows else None

    def _fetch_all(self, query: str, params: Iterable[Any]) -> list[dict[str, Any]]:
        connection = self.connection_factory(self.db_name)
        try:
            with connection.cursor(cursor_factory=RealDictCursor) as cursor:
                cursor.execute(query, tuple(params))
                return [dict(row) for row in cursor.fetchall()]
        finally:
            connection.close()

    @staticmethod
    def _upsert_filing(cursor, collected: CollectedSecFiling) -> None:
        filing = collected.filing
        cursor.execute(
            """
            INSERT INTO public.sec_filings (
                accession_number, cik, ticker, company_name, form_type,
                filing_date, accepted_at, report_date, primary_document,
                primary_doc_description, item_codes, event_type, sec_url,
                metadata_json
            )
            VALUES (
                %s, %s, %s, %s, %s,
                %s, %s, %s, %s,
                %s, %s, %s, %s,
                %s
            )
            ON CONFLICT (accession_number) DO UPDATE SET
                cik = EXCLUDED.cik,
                ticker = EXCLUDED.ticker,
                company_name = EXCLUDED.company_name,
                form_type = EXCLUDED.form_type,
                filing_date = EXCLUDED.filing_date,
                accepted_at = EXCLUDED.accepted_at,
                report_date = EXCLUDED.report_date,
                primary_document = EXCLUDED.primary_document,
                primary_doc_description = EXCLUDED.primary_doc_description,
                item_codes = EXCLUDED.item_codes,
                event_type = EXCLUDED.event_type,
                sec_url = EXCLUDED.sec_url,
                metadata_json = EXCLUDED.metadata_json,
                updated_at = NOW()
            """,
            (
                filing.accession_number,
                filing.cik,
                filing.ticker,
                filing.company_name,
                filing.form_type,
                filing.filing_date,
                filing.accepted_at,
                filing.report_date,
                filing.primary_document,
                filing.primary_doc_description,
                ",".join(filing.item_codes),
                filing.event_type,
                filing.sec_url,
                Json(filing.metadata),
            ),
        )

    @staticmethod
    def _upsert_documents(cursor, collected: CollectedSecFiling) -> None:
        records = [
            (
                collected.filing.accession_number,
                document.sequence,
                document.document_name,
                document.document_type,
                document.description,
                document.source_url,
                str(document.local_path) if document.local_path else None,
                document.content_type,
                document.content_text,
                document.content_hash,
                document.is_primary,
                document.is_exhibit,
            )
            for document in collected.documents
        ]
        if not records:
            return
        execute_values(
            cursor,
            """
            INSERT INTO public.sec_filing_documents (
                accession_number, sequence_number, document_name, document_type,
                description, source_url, local_path, content_type, content_text,
                content_hash, is_primary, is_exhibit
            )
            VALUES %s
            ON CONFLICT (accession_number, document_name) DO UPDATE SET
                sequence_number = EXCLUDED.sequence_number,
                document_type = EXCLUDED.document_type,
                description = EXCLUDED.description,
                source_url = EXCLUDED.source_url,
                local_path = EXCLUDED.local_path,
                content_type = EXCLUDED.content_type,
                content_text = EXCLUDED.content_text,
                content_hash = EXCLUDED.content_hash,
                is_primary = EXCLUDED.is_primary,
                is_exhibit = EXCLUDED.is_exhibit,
                updated_at = NOW()
            """,
            records,
        )

    @staticmethod
    def _upsert_transactions(cursor, collected: CollectedSecFiling) -> None:
        records = [
            (
                collected.filing.accession_number,
                transaction.transaction_index,
                transaction.security_category,
                transaction.reporting_owner_cik,
                transaction.reporting_owner_name,
                transaction.is_director,
                transaction.is_officer,
                transaction.is_ten_percent_owner,
                transaction.is_other,
                transaction.officer_title,
                transaction.security_title,
                transaction.transaction_date,
                transaction.transaction_code,
                transaction.transaction_type,
                transaction.acquired_disposed_code,
                transaction.shares,
                transaction.price_per_share,
                transaction.shares_owned_after,
                transaction.ownership_form,
                transaction.is_derivative,
                transaction.is_signal,
                ",".join(transaction.footnote_ids),
            )
            for transaction in collected.insider_transactions
        ]
        if not records:
            return
        execute_values(
            cursor,
            """
            INSERT INTO public.sec_insider_transactions (
                accession_number, transaction_index, security_category,
                reporting_owner_cik, reporting_owner_name, is_director,
                is_officer, is_ten_percent_owner, is_other, officer_title,
                security_title, transaction_date, transaction_code,
                transaction_type, acquired_disposed_code, shares,
                price_per_share, shares_owned_after, ownership_form,
                is_derivative, is_signal, footnote_ids
            )
            VALUES %s
            ON CONFLICT (accession_number, security_category, transaction_index)
            DO UPDATE SET
                reporting_owner_cik = EXCLUDED.reporting_owner_cik,
                reporting_owner_name = EXCLUDED.reporting_owner_name,
                is_director = EXCLUDED.is_director,
                is_officer = EXCLUDED.is_officer,
                is_ten_percent_owner = EXCLUDED.is_ten_percent_owner,
                is_other = EXCLUDED.is_other,
                officer_title = EXCLUDED.officer_title,
                security_title = EXCLUDED.security_title,
                transaction_date = EXCLUDED.transaction_date,
                transaction_code = EXCLUDED.transaction_code,
                transaction_type = EXCLUDED.transaction_type,
                acquired_disposed_code = EXCLUDED.acquired_disposed_code,
                shares = EXCLUDED.shares,
                price_per_share = EXCLUDED.price_per_share,
                shares_owned_after = EXCLUDED.shares_owned_after,
                ownership_form = EXCLUDED.ownership_form,
                is_derivative = EXCLUDED.is_derivative,
                is_signal = EXCLUDED.is_signal,
                footnote_ids = EXCLUDED.footnote_ids,
                updated_at = NOW()
            """,
            records,
        )
