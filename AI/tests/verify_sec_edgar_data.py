from __future__ import annotations

import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import MagicMock, Mock, patch


PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.append(str(PROJECT_ROOT))

from AI.modules.data_collector.components.sec_edgar_client import (
    SecEdgarClient,
    SecHttpResponse,
)
from AI.modules.data_collector.components.sec_edgar_data import (
    SecEdgarCollectorConfig,
    SecEdgarDataCollector,
)
from AI.modules.data_collector.components.sec_edgar_models import SecCompany
from AI.modules.data_collector.components.sec_edgar_parser import (
    document_to_text,
    filing_from_submission_row,
    flatten_submission_rows,
    parse_filing_index,
    parse_form4_xml,
)
from AI.modules.data_collector.components.sec_edgar_query import (
    SecFilingFileQuery,
    to_json_text,
)
from AI.modules.data_collector.components.sec_edgar_repository import (
    SecFilingRepository,
)
from AI.modules.data_collector.scripts.collect_sec_edgar import (
    _load_universe_tickers,
    main as collect_cli_main,
    parse_args as parse_collect_args,
)
from AI.modules.data_collector.scripts.query_sec_filings import main as query_cli_main


FIXTURE_DIR = Path(__file__).parent / "fixtures/sec"


class SecCollectorCliTest(unittest.TestCase):
    def test_universe에서_활성_티커만_중복없이_읽는다(self):
        payload = {
            "companies": [
                {"enabled": True, "tickers": ["aapl", "GOOG"]},
                {"enabled": True, "tickers": ["GOOG", "GOOGL"]},
                {"enabled": False, "tickers": ["DISABLED"]},
            ]
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            universe_path = Path(temp_dir) / "universe.json"
            universe_path.write_text(json.dumps(payload), encoding="utf-8")

            tickers = _load_universe_tickers(universe_path)

        self.assertEqual(["AAPL", "GOOG", "GOOGL"], tickers)

    def test_start와_lookback_days는_함께_사용할_수_없다(self):
        with self.assertRaises(SystemExit):
            parse_collect_args(
                ["--tickers", "AAPL", "--start", "2026-08-01", "--lookback-days", "7"]
            )

    @patch(
        "AI.modules.data_collector.scripts.collect_sec_edgar.SecEdgarDataCollector"
    )
    def test_일부_공시_실패시_비정상_종료한다(self, collector_class):
        collector_class.return_value.__enter__.return_value.collect.return_value = {
            "companies": 1,
            "filings": 1,
            "documents": 1,
            "transactions": 0,
            "failed": 1,
        }

        with self.assertRaises(SystemExit) as raised:
            collect_cli_main(
                [
                    "--tickers",
                    "AAPL",
                    "--user-agent",
                    "SISC Test test@example.com",
                    "--recent-only",
                ]
            )

        self.assertEqual(1, raised.exception.code)


class FakeSecClient:
    def __init__(self, *, json_payloads=None, responses=None):
        self.json_payloads = json_payloads or {}
        self.responses = responses or {}
        self.requested_urls: list[str] = []

    def get_json(self, url: str, *, immutable: bool = False):
        self.requested_urls.append(url)
        return self.json_payloads[url]

    def get(self, url: str, *, immutable: bool = False):
        self.requested_urls.append(url)
        return self.responses[url]


class FakeRequestsResponse:
    def __init__(self, status_code: int, content: bytes, url: str):
        self.status_code = status_code
        self.content = content
        self.url = url
        self.headers = {"Content-Type": "application/json"}


class FakeSession:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def get(self, url, **kwargs):
        self.calls.append((url, kwargs))
        return self.responses.pop(0)


class SecEdgarParserTest(unittest.TestCase):
    def test_submissions_컬럼_배열을_공시별로_변환한다(self):
        payload = json.loads(
            (FIXTURE_DIR / "submissions_sample.json").read_text(encoding="utf-8")
        )

        rows = flatten_submission_rows(payload)

        self.assertEqual(3, len(rows))
        self.assertEqual("0000320193-26-000001", rows[0]["accessionNumber"])
        self.assertEqual("8-K", rows[0]["form"])
        filing = filing_from_submission_row(
            rows[0],
            cik="320193",
            ticker="samp",
            company_name="Sample Corporation",
        )
        self.assertEqual("EARNINGS_8K_2_02", filing.event_type)
        self.assertEqual(("2.02", "9.01"), filing.item_codes)
        self.assertEqual("America/New_York", str(filing.accepted_at.tzinfo))

    def test_공시_인덱스에서_원문과_99_1을_찾는다(self):
        html = (FIXTURE_DIR / "filing_index.html").read_text(encoding="utf-8")

        documents = parse_filing_index(
            html,
            index_url=(
                "https://www.sec.gov/Archives/edgar/data/320193/"
                "000032019326000001/0000320193-26-000001-index.html"
            ),
            primary_document="sample-20260720.htm",
        )

        self.assertEqual(3, len(documents))
        self.assertTrue(documents[0].is_primary)
        self.assertTrue(documents[1].is_exhibit)
        self.assertEqual("EX-99.1", documents[1].document_type)

    def test_form4는_xsl_html이_아닌_원본_xml을_primary로_선택한다(self):
        html = """
        <table class="tableFile" summary="Document Format Files">
          <tr>
            <td>1</td><td>FORM 4</td>
            <td><a href="/Archives/edgar/data/707549/0001/xslF345X06/ownership.xml">ownership.xml</a></td>
            <td>4</td>
          </tr>
          <tr>
            <td>1</td><td>FORM 4</td>
            <td><a href="/Archives/edgar/data/707549/0001/ownership.xml">ownership.xml</a></td>
            <td>4</td>
          </tr>
        </table>
        """

        documents = parse_filing_index(
            html,
            index_url=(
                "https://www.sec.gov/Archives/edgar/data/1343600/0001/"
                "0001343600-26-000011-index.html"
            ),
            primary_document="xslF345X06/ownership.xml",
        )

        primary = [document for document in documents if document.is_primary]
        self.assertEqual(1, len(primary))
        self.assertEqual("ownership.xml", primary[0].document_name)
        self.assertEqual(
            "https://www.sec.gov/Archives/edgar/data/707549/0001/ownership.xml",
            primary[0].source_url,
        )

    def test_html을_스크립트가_제거된_평문으로_바꾼다(self):
        content = (FIXTURE_DIR / "eight_k.html").read_bytes()

        text = document_to_text(content, "text/html; charset=utf-8", "sample.htm")

        self.assertIn("Item 2.02", text)
        self.assertIn("$12.4 billion", text)
        self.assertNotIn("console.log", text)

    def test_form4의_p_s만_신호로_분류한다(self):
        transactions = parse_form4_xml((FIXTURE_DIR / "form4.xml").read_bytes())

        self.assertEqual(3, len(transactions))
        self.assertEqual(["P", "S", "M"], [item.transaction_code for item in transactions])
        self.assertEqual([True, True, False], [item.is_signal for item in transactions])
        self.assertEqual(
            ["PURCHASE", "SALE", "OTHER"],
            [item.transaction_type for item in transactions],
        )
        self.assertFalse(transactions[0].is_derivative)
        self.assertTrue(transactions[2].is_derivative)
        self.assertEqual("Chief Financial Officer", transactions[0].officer_title)


class SecEdgarClientTest(unittest.TestCase):
    def test_제한_응답을_재시도하고_성공_응답을_캐시한다(self):
        url = "https://data.sec.gov/submissions/CIK0000320193.json"
        session = FakeSession(
            [
                FakeRequestsResponse(429, b"{}", url),
                FakeRequestsResponse(200, b'{"name":"Sample"}', url),
            ]
        )
        sleeps: list[float] = []

        with tempfile.TemporaryDirectory() as temp_dir:
            client = SecEdgarClient(
                "SISC Test test@example.com",
                cache_dir=Path(temp_dir),
                session=session,
                sleeper=sleeps.append,
                max_retries=2,
            )

            first = client.get_json(url)
            second = client.get_json(url)

        self.assertEqual({"name": "Sample"}, first)
        self.assertEqual(first, second)
        self.assertEqual(2, len(session.calls))
        self.assertGreaterEqual(len(sleeps), 1)
        self.assertEqual(
            "SISC Test test@example.com",
            session.calls[0][1]["headers"]["User-Agent"],
        )


class SecEdgarCollectorTest(unittest.TestCase):
    def test_cik_직접_지정은_티커_매핑_API_없이_실행한다(self):
        payload = json.loads(
            (FIXTURE_DIR / "submissions_sample.json").read_text(encoding="utf-8")
        )
        submissions_url = "https://data.sec.gov/submissions/CIK0000320193.json"
        client = FakeSecClient(json_payloads={submissions_url: payload})

        with tempfile.TemporaryDirectory() as temp_dir:
            config = self._config(Path(temp_dir)).with_overrides(
                start_date="2030-01-01",
                end_date="2030-12-31",
            )
            collector = SecEdgarDataCollector(
                config,
                client=client,
                repository=Mock(),
            )

            stats = collector.collect(ciks=["320193"])
            collector.close()

        self.assertEqual(1, stats["companies"])
        self.assertEqual(0, stats["filings"])
        self.assertEqual([submissions_url], client.requested_urls)

    def test_대상_item과_form4만_수집_목록에_포함한다(self):
        payload = json.loads(
            (FIXTURE_DIR / "submissions_sample.json").read_text(encoding="utf-8")
        )
        submissions_url = (
            "https://data.sec.gov/submissions/CIK0000320193.json"
        )
        client = FakeSecClient(json_payloads={submissions_url: payload})

        with tempfile.TemporaryDirectory() as temp_dir:
            config = self._config(Path(temp_dir))
            collector = SecEdgarDataCollector(
                config,
                client=client,
                repository=Mock(),
            )
            filings = list(
                collector._iter_company_filings(
                    SecCompany(
                        cik="0000320193",
                        ticker="SAMP",
                        company_name="Sample Corporation",
                    )
                )
            )
            collector.close()

        self.assertEqual(2, len(filings))
        self.assertEqual(
            {"EARNINGS_8K_2_02", "FORM_4"},
            {filing.event_type for filing in filings},
        )

    def test_8k_원문과_exhibit를_파일로_저장한다(self):
        payload = json.loads(
            (FIXTURE_DIR / "submissions_sample.json").read_text(encoding="utf-8")
        )
        filing = filing_from_submission_row(
            flatten_submission_rows(payload)[0],
            cik="0000320193",
            ticker="SAMP",
            company_name="Sample Corporation",
        )
        index_html = (FIXTURE_DIR / "filing_index.html").read_bytes()
        primary_url = (
            "https://www.sec.gov/Archives/edgar/data/320193/"
            "000032019326000001/sample-20260720.htm"
        )
        exhibit_url = (
            "https://www.sec.gov/Archives/edgar/data/320193/"
            "000032019326000001/exhibit991.htm"
        )
        responses = {
            filing.sec_url: SecHttpResponse(
                filing.sec_url,
                200,
                index_html,
                {"content-type": "text/html; charset=utf-8"},
            ),
            primary_url: SecHttpResponse(
                primary_url,
                200,
                (FIXTURE_DIR / "eight_k.html").read_bytes(),
                {"content-type": "text/html; charset=utf-8"},
            ),
            exhibit_url: SecHttpResponse(
                exhibit_url,
                200,
                b"<html><body>Press release exhibit</body></html>",
                {"content-type": "text/html; charset=utf-8"},
            ),
        }

        with tempfile.TemporaryDirectory() as temp_dir:
            config = self._config(Path(temp_dir))
            collector = SecEdgarDataCollector(
                config,
                client=FakeSecClient(responses=responses),
                repository=Mock(),
            )

            collected = collector.collect_filing(filing)
            collector._save(collected)

            metadata_path = (
                config.data_dir
                / filing.cik
                / filing.accession_compact
                / "filing.json"
            )
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            raw_files_exist = all(
                document.local_path.exists() for document in collected.documents
            )
            file_query = SecFilingFileQuery(config.data_dir)
            summaries = file_query.list_filings(ticker="SAMP", form_type="8-K")
            detail_without_content = file_query.get_filing(filing.accession_number)
            document = file_query.get_document(
                filing.accession_number,
                document_name="exhibit991.htm",
            )
            collector.close()

        self.assertEqual(2, len(collected.documents))
        self.assertTrue(raw_files_exist)
        self.assertEqual(2, len(metadata["documents"]))
        self.assertIn("Item 2.02", collected.documents[0].content_text)
        self.assertEqual(1, len(summaries))
        self.assertEqual(2, summaries[0]["document_count"])
        self.assertNotIn(
            "content_text",
            detail_without_content["documents"][0],
        )
        self.assertIn("Press release", document["content_text"])

    def test_form4_원문을_수집하고_거래를_구조화한다(self):
        payload = json.loads(
            (FIXTURE_DIR / "submissions_sample.json").read_text(encoding="utf-8")
        )
        filing = filing_from_submission_row(
            flatten_submission_rows(payload)[1],
            cik="0000320193",
            ticker="SAMP",
            company_name="Sample Corporation",
        )
        document_url = (
            "https://www.sec.gov/Archives/edgar/data/320193/"
            "000032019326000002/ownership.xml"
        )
        index_html = f"""
        <table class="tableFile">
          <tr>
            <td>1</td><td>FORM 4</td>
            <td><a href="{document_url}">ownership.xml</a></td>
            <td>4</td><td>1000</td>
          </tr>
        </table>
        """.encode()
        responses = {
            filing.sec_url: SecHttpResponse(
                filing.sec_url,
                200,
                index_html,
                {"content-type": "text/html; charset=utf-8"},
            ),
            document_url: SecHttpResponse(
                document_url,
                200,
                (FIXTURE_DIR / "form4.xml").read_bytes(),
                {"content-type": "application/xml"},
            ),
        }

        with tempfile.TemporaryDirectory() as temp_dir:
            collector = SecEdgarDataCollector(
                self._config(Path(temp_dir)),
                client=FakeSecClient(responses=responses),
                repository=Mock(),
            )

            collected = collector.collect_filing(filing)
            collector._save(collected)
            p_signal_filings = SecFilingFileQuery(
                collector.config.data_dir
            ).list_filings(signals_only=True, signal_code="P")
            collector.close()

        self.assertEqual(1, len(collected.documents))
        self.assertEqual(3, len(collected.insider_transactions))
        self.assertEqual(
            2,
            sum(item.is_signal for item in collected.insider_transactions),
        )
        self.assertEqual(1, len(p_signal_filings))

    @staticmethod
    def _config(root: Path) -> SecEdgarCollectorConfig:
        return SecEdgarCollectorConfig(
            user_agent="SISC Test test@example.com",
            start_date="2026-07-01",
            end_date="2026-07-31",
            storage="file",
            data_dir=root / "data",
            cache_dir=root / "cache",
            log_dir=root / "logs",
            include_historical_files=False,
        )


class SecFilingRepositoryQueryTest(unittest.TestCase):
    def test_db_목록_조회에_필터와_p신호_조건을_적용한다(self):
        connection = MagicMock()
        cursor = MagicMock()
        connection.cursor.return_value.__enter__.return_value = cursor
        cursor.fetchall.return_value = [
            {
                "accession_number": "0000320193-26-000002",
                "ticker": "SAMP",
                "signal_count": 1,
            }
        ]
        factory = Mock(return_value=connection)
        repository = SecFilingRepository(
            "db",
            connection_factory=factory,
        )

        result = repository.list_filings(
            ticker="SAMP",
            signals_only=True,
            signal_code="P",
            limit=20,
        )

        query, params = cursor.execute.call_args.args
        self.assertEqual(1, len(result))
        self.assertIn("EXISTS", query)
        self.assertIn("t.transaction_code = %s", query)
        self.assertEqual(("SAMP", "P", 20, 0), params)
        connection.close.assert_called_once_with()

    def test_db_상세_조회는_공시_문서_거래를_묶어_반환한다(self):
        connection = MagicMock()
        cursor = MagicMock()
        connection.cursor.return_value.__enter__.return_value = cursor
        cursor.fetchone.return_value = {
            "accession_number": "0000320193-26-000002",
            "ticker": "SAMP",
        }
        cursor.fetchall.side_effect = [
            [{"document_name": "ownership.xml"}],
            [{"transaction_code": "P", "is_signal": True}],
        ]
        repository = SecFilingRepository(
            "db",
            connection_factory=Mock(return_value=connection),
        )

        result = repository.get_filing(
            "0000320193-26-000002",
            include_content=True,
        )

        self.assertEqual("SAMP", result["filing"]["ticker"])
        self.assertEqual("ownership.xml", result["documents"][0]["document_name"])
        self.assertTrue(result["insider_transactions"][0]["is_signal"])
        document_query = cursor.execute.call_args_list[1].args[0]
        self.assertIn("d.content_text", document_query)
        connection.close.assert_called_once_with()

    def test_json_출력은_날짜와_decimal을_문자열로_변환한다(self):
        from datetime import date
        from decimal import Decimal

        output = to_json_text(
            {"date": date(2026, 7, 23), "shares": Decimal("10.500000")}
        )

        self.assertIn('"2026-07-23"', output)
        self.assertIn('"10.500000"', output)


class SecFilingQueryCliTest(unittest.TestCase):
    def test_file_소스_공시_목록을_cli_json으로_출력한다(self):
        payload = {
            "filing": {
                "accession_number": "0000320193-26-000001",
                "cik": "0000320193",
                "ticker": "SAMP",
                "company_name": "Sample Corporation",
                "form_type": "8-K",
                "filing_date": "2026-07-20",
                "accepted_at": "2026-07-20T16:12:30-04:00",
                "event_type": "EARNINGS_8K_2_02",
            },
            "documents": [{"document_name": "sample.htm"}],
            "insider_transactions": [],
        }

        with tempfile.TemporaryDirectory() as temp_dir:
            filing_dir = (
                Path(temp_dir)
                / "0000320193"
                / "000032019326000001"
            )
            filing_dir.mkdir(parents=True)
            (filing_dir / "filing.json").write_text(
                json.dumps(payload),
                encoding="utf-8",
            )
            stdout = io.StringIO()
            argv = [
                "query_sec_filings.py",
                "--source",
                "file",
                "--data-dir",
                temp_dir,
                "--ticker",
                "SAMP",
                "--compact",
            ]

            with patch.object(sys, "argv", argv), redirect_stdout(stdout):
                query_cli_main()

        result = json.loads(stdout.getvalue())
        self.assertEqual(1, len(result))
        self.assertEqual("0000320193-26-000001", result[0]["accession_number"])


if __name__ == "__main__":
    unittest.main()
