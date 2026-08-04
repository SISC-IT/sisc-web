from __future__ import annotations

# ruff: noqa: E402

import unittest
import json
import tempfile
from datetime import datetime, timezone
from pathlib import Path
import sys
from unittest.mock import MagicMock

import requests

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.append(str(PROJECT_ROOT))

from AI.modules.data_collector.components.news.config import (
    DEFAULT_CONFIG_PATH,
    NewsCollectionConfig,
    load_company_universe,
)
from AI.modules.data_collector.components.news.contracts import (
    CollectionStatus,
    CompanyTarget,
    ProviderArticle,
)
from AI.modules.data_collector.components.news.dedup import (
    deduplicate_exact,
    normalize_url,
    syndication_candidate_group_id,
)
from AI.modules.data_collector.components.news.pipeline import CompanyNewsCollector
from AI.modules.data_collector.components.news.providers.base import (
    ProviderFetchResult,
    ProviderFetchStatus,
)
from AI.modules.data_collector.components.news.providers.google_news_rss import (
    GoogleNewsRssProvider,
)
from AI.modules.data_collector.components.news.relevance import (
    score_company_relevance,
)
from AI.modules.data_collector.components.news.windows import event_news_window
from AI.modules.data_collector.components.news_data import _temporary_target
from AI.modules.data_collector.scripts.collect_company_news import (
    _resolve_window,
    main,
    parse_args,
)

FIXTURE_PATH = PROJECT_ROOT / "AI/tests/fixtures/news/google_news_rss.xml"
UTC = timezone.utc


class FakeResponse:
    def __init__(
        self,
        content: bytes,
        *,
        status_code: int = 200,
        content_type: str = "application/rss+xml; charset=utf-8",
        content_length: int | None = None,
    ) -> None:
        self.content = content
        self.status_code = status_code
        self.headers = {"Content-Type": content_type}
        if content_length is not None:
            self.headers["Content-Length"] = str(content_length)

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            response = requests.Response()
            response.status_code = self.status_code
            raise requests.HTTPError(
                f"{self.status_code} test response",
                response=response,
            )

    def iter_content(self, chunk_size: int):
        del chunk_size
        yield self.content

    def close(self) -> None:
        return None


def apple_target() -> CompanyTarget:
    return CompanyTarget(
        company_key="apple",
        cik="0000320193",
        legal_name="Apple Inc.",
        tickers=("AAPL",),
        aliases=("Apple",),
    )


def article(
    *,
    provider_article_id: str | None = "id-1",
    title: str = "Apple reports record earnings",
    url: str = "https://example.com/story?id=1",
    published_at: datetime | None = None,
    snippet: str | None = "Apple stock rose after the report.",
) -> ProviderArticle:
    timestamp = published_at or datetime(2026, 7, 28, 14, tzinfo=UTC)
    return ProviderArticle(
        provider="test_provider",
        provider_article_id=provider_article_id,
        title=title,
        provider_url=url,
        published_at_raw="Tue, 28 Jul 2026 14:00:00 GMT",
        published_at_utc=timestamp,
        source="Example Wire",
        snippet=snippet,
    )


class GoogleNewsRssProviderTest(unittest.TestCase):
    def _provider(
        self, response: FakeResponse
    ) -> tuple[GoogleNewsRssProvider, MagicMock]:
        session = MagicMock()
        session.get.return_value = response
        return GoogleNewsRssProvider(session=session), session

    def test_RSS를_파싱하고_원본시각과_UTC시각을_모두_보존한다(self) -> None:
        provider, session = self._provider(FakeResponse(FIXTURE_PATH.read_bytes()))

        result = provider.fetch_company_news(
            apple_target(),
            start_at=datetime(2026, 7, 28, 12, tzinfo=UTC),
            end_at=datetime(2026, 7, 28, 18, tzinfo=UTC),
            limit=10,
        )

        self.assertEqual(result.status, ProviderFetchStatus.SUCCESS)
        self.assertEqual(len(result.articles), 2)
        self.assertEqual(
            result.articles[0].published_at_raw,
            "Tue, 28 Jul 2026 14:00:00 GMT",
        )
        self.assertEqual(
            result.articles[1].published_at_utc,
            datetime(2026, 7, 28, 17, 30, tzinfo=UTC),
        )
        self.assertEqual(
            result.articles[0].snippet,
            "Apple raised its production outlook.",
        )
        self.assertEqual(result.invalid_item_count, 1)

        call = session.get.call_args
        self.assertEqual(call.args[0], GoogleNewsRssProvider.DEFAULT_ENDPOINT)
        self.assertIn('"Apple Inc."', call.kwargs["params"]["q"])
        self.assertIn('"AAPL stock"', call.kwargs["params"]["q"])
        self.assertFalse(call.kwargs["allow_redirects"])

    def test_정상_빈피드는_no_results이다(self) -> None:
        provider, _ = self._provider(
            FakeResponse(b"<?xml version='1.0'?><rss><channel /></rss>")
        )
        result = provider.fetch_company_news(
            apple_target(),
            start_at=datetime(2026, 7, 28, 12, tzinfo=UTC),
            end_at=datetime(2026, 7, 28, 18, tzinfo=UTC),
        )
        self.assertEqual(result.status, ProviderFetchStatus.NO_RESULTS)
        self.assertEqual(result.raw_item_count, 0)

    def test_item이_모두_invalid면_정상_0건으로_숨기지_않는다(self) -> None:
        payload = b"""<?xml version="1.0"?>
        <rss><channel><item><title>missing link and time</title></item></channel></rss>"""
        provider, _ = self._provider(FakeResponse(payload))
        result = provider.fetch_company_news(
            apple_target(),
            start_at=datetime(2026, 7, 28, 12, tzinfo=UTC),
            end_at=datetime(2026, 7, 28, 18, tzinfo=UTC),
        )
        self.assertEqual(result.status, ProviderFetchStatus.ERROR)
        self.assertEqual(result.invalid_item_count, 1)
        self.assertIn("invalid_feed", result.errors[0])

    def test_깨진_XML과_DTD와_크기초과를_provider_error로_처리한다(self) -> None:
        payloads = (
            FakeResponse(b"<rss><channel>"),
            FakeResponse(b"<!DOCTYPE rss><rss><channel /></rss>"),
            FakeResponse(
                b"<rss><channel /></rss>",
                content_length=2_000_001,
            ),
        )
        for response in payloads:
            with self.subTest(payload=response.content):
                provider, _ = self._provider(response)
                result = provider.fetch_company_news(
                    apple_target(),
                    start_at=datetime(2026, 7, 28, 12, tzinfo=UTC),
                    end_at=datetime(2026, 7, 28, 18, tzinfo=UTC),
                )
                self.assertEqual(result.status, ProviderFetchStatus.ERROR)

    def test_HTTP장애와_빈피드는_서로_다른_상태이다(self) -> None:
        provider, _ = self._provider(FakeResponse(b"", status_code=503))
        failed = provider.fetch_company_news(
            apple_target(),
            start_at=datetime(2026, 7, 28, 12, tzinfo=UTC),
            end_at=datetime(2026, 7, 28, 18, tzinfo=UTC),
        )
        self.assertEqual(failed.status, ProviderFetchStatus.ERROR)
        self.assertTrue(failed.errors)
        self.assertEqual(failed.failure_reason, "source_http_503")

    def test_timeout은_source_timeout으로_분류한다(self) -> None:
        session = MagicMock()
        session.get.side_effect = requests.Timeout("test timeout")
        provider = GoogleNewsRssProvider(session=session)
        result = provider.fetch_company_news(
            apple_target(),
            start_at=datetime(2026, 7, 28, 12, tzinfo=UTC),
            end_at=datetime(2026, 7, 28, 18, tzinfo=UTC),
        )
        self.assertEqual(result.status, ProviderFetchStatus.ERROR)
        self.assertEqual(result.failure_reason, "source_timeout")


class NormalizationAndRelevanceTest(unittest.TestCase):
    def test_URL은_추적값만_제거하고_의미있는_query를_보존한다(self) -> None:
        result = normalize_url(
            "HTTPS://Example.COM:443/news?id=42&utm_source=rss" "&tag=b&tag=a#section"
        )
        self.assertEqual(
            result,
            "https://example.com/news?id=42&tag=a&tag=b",
        )
        self.assertIsNone(normalize_url("javascript:alert(1)"))
        self.assertIsNone(normalize_url("https://user:secret@example.com/story"))

    def test_회사명_별칭과_ticker_토큰을_판정한다(self) -> None:
        target = apple_target()
        by_name = score_company_relevance(
            article(title="Apple expands services", snippet=None),
            target,
        )
        by_ticker = score_company_relevance(
            article(title="AAPL stock rises after earnings", snippet=None),
            target,
        )
        false_boundary = score_company_relevance(
            article(title="AAPLX fund rises", snippet=None),
            target,
        )
        lifestyle = score_company_relevance(
            article(
                title="Your Apple Watch Band Is Ugly. Wear a Nicer One.",
                snippet=None,
            ),
            target,
        )

        self.assertGreaterEqual(by_name.score, 0.5)
        self.assertEqual(by_name.matched_ticker, None)
        self.assertGreaterEqual(by_ticker.score, 0.5)
        self.assertEqual(by_ticker.matched_ticker, "AAPL")
        self.assertEqual(false_boundary.score, 0)
        self.assertLess(lifestyle.score, 0.6)

    def test_모호한_ticker는_단독으로_관련성을_만들지_않는다(self) -> None:
        target = CompanyTarget(
            company_key="visa",
            cik="0001403161",
            legal_name="Visa Inc.",
            tickers=("V",),
            aliases=("Visa",),
            ambiguous_tickers=("V",),
        )
        only_ticker = score_company_relevance(
            article(title="$V stock jumps", snippet=None),
            target,
        )
        corroborated = score_company_relevance(
            article(title="Visa ($V) stock jumps", snippet=None),
            target,
        )
        self.assertEqual(only_ticker.score, 0)
        self.assertGreaterEqual(corroborated.score, 0.5)

        caterpillar = CompanyTarget(
            company_key="caterpillar",
            cik="0000018230",
            legal_name="Caterpillar Inc.",
            tickers=("CAT",),
            aliases=("Caterpillar",),
            ambiguous_tickers=("CAT",),
        )
        normal_word = score_company_relevance(
            article(title="Cat photos are popular", snippet=None),
            caterpillar,
        )
        uppercase_stock = score_company_relevance(
            article(title="CAT stock rises after earnings", snippet=None),
            caterpillar,
        )
        self.assertEqual(normal_word.score, 0)
        self.assertGreaterEqual(uppercase_stock.score, 0.6)
        self.assertEqual(uppercase_stock.score, 0.6)
        self.assertNotIn("business_or_market_context", uppercase_stock.reasons)


class DeduplicationTest(unittest.TestCase):
    def test_GUID나_normalized_URL이_같으면_정확중복만_제거한다(self) -> None:
        same_guid = article(url="https://one.example/story", provider_article_id="same")
        same_guid_again = article(
            url="https://two.example/story",
            provider_article_id="same",
        )
        same_url = article(
            url="https://example.com/story?id=1&utm_source=feed",
            provider_article_id="other",
        )
        result = deduplicate_exact((same_guid, same_guid_again, same_url))
        self.assertEqual(len(result.articles), 2)
        self.assertEqual(result.duplicate_count, 1)

        url_result = deduplicate_exact(
            (
                article(provider_article_id=None),
                article(
                    provider_article_id="new-guid",
                    url="https://example.com/story?utm_medium=rss&id=1",
                ),
            )
        )
        self.assertEqual(len(url_result.articles), 1)
        self.assertEqual(url_result.duplicate_count, 1)

        transitive = deduplicate_exact(
            (
                article(provider_article_id="id-a", url="https://example.com/a"),
                article(provider_article_id="id-a", url="https://example.com/b"),
                article(provider_article_id="id-b", url="https://example.com/b"),
            )
        )
        self.assertEqual(len(transitive.articles), 1)
        self.assertEqual(transitive.duplicate_count, 2)

    def test_같은제목_다른URL은_유지하고_candidate_group만_같다(self) -> None:
        first = article(
            provider_article_id="wire-1",
            url="https://wire-one.example/story",
        )
        second = article(
            provider_article_id="wire-2",
            url="https://wire-two.example/story",
            published_at=datetime(2026, 7, 28, 15, tzinfo=UTC),
        )
        result = deduplicate_exact((first, second))
        self.assertEqual(len(result.articles), 2)
        self.assertEqual(
            syndication_candidate_group_id(first),
            syndication_candidate_group_id(second),
        )

        source_one = article(
            provider_article_id="publisher-1",
            title="Apple reports record earnings - Example Wire",
        )
        source_two = ProviderArticle(
            provider="test_provider",
            provider_article_id="publisher-2",
            title="Apple reports record earnings - Second Outlet",
            provider_url="https://second.example/story",
            published_at_raw="Tue, 28 Jul 2026 15:00:00 GMT",
            published_at_utc=datetime(2026, 7, 28, 15, tzinfo=UTC),
            source="Second Outlet",
        )
        self.assertEqual(
            syndication_candidate_group_id(source_one),
            syndication_candidate_group_id(source_two),
        )


class PipelineTest(unittest.TestCase):
    def test_관련기사없음과_provider장애를_구분한다(self) -> None:
        provider = MagicMock()
        provider.name = "fake"
        provider.fetch_company_news.side_effect = (
            ProviderFetchResult(
                status=ProviderFetchStatus.SUCCESS,
                articles=(
                    article(
                        title="Unrelated sports result",
                        snippet="A team won the game.",
                    ),
                ),
                query_count=1,
                raw_item_count=1,
            ),
            ProviderFetchResult(
                status=ProviderFetchStatus.ERROR,
                query_count=1,
                errors=("source_timeout",),
            ),
        )
        collector = CompanyNewsCollector(provider)
        window = {
            "start_at": datetime(2026, 7, 28, 12, tzinfo=UTC),
            "end_at": datetime(2026, 7, 28, 18, tzinfo=UTC),
        }

        no_relevant = collector.collect_company(apple_target(), **window)
        failed = collector.collect_company(apple_target(), **window)

        self.assertEqual(no_relevant.status, CollectionStatus.NO_RELEVANT_NEWS)
        self.assertEqual(no_relevant.relevant_article_count, 0)
        self.assertEqual(failed.status, CollectionStatus.PROVIDER_ERROR)
        self.assertIsNone(failed.relevant_article_count)
        self.assertEqual(no_relevant.to_dict()["collection_status"], "success")
        self.assertEqual(
            no_relevant.to_dict()["collection_outcome"],
            "no_relevant_news",
        )
        self.assertEqual(failed.to_dict()["collection_status"], "failed")
        self.assertEqual(failed.to_dict()["collection_outcome"], "provider_error")

    def test_회사하나의_실패가_batch를_중단시키지_않는다(self) -> None:
        provider = MagicMock()
        provider.name = "fake"
        provider.fetch_company_news.side_effect = (
            ProviderFetchResult(
                status=ProviderFetchStatus.SUCCESS,
                articles=(article(),),
                query_count=1,
                raw_item_count=1,
            ),
            ProviderFetchResult(
                status=ProviderFetchStatus.ERROR,
                query_count=1,
                errors=("source_timeout",),
            ),
        )
        microsoft = CompanyTarget(
            company_key="microsoft",
            cik="0000789019",
            legal_name="Microsoft Corporation",
            tickers=("MSFT",),
            aliases=("Microsoft",),
        )
        collector = CompanyNewsCollector(provider)
        result = collector.collect_batch(
            (apple_target(), microsoft),
            start_at=datetime(2026, 7, 28, 12, tzinfo=UTC),
            end_at=datetime(2026, 7, 28, 18, tzinfo=UTC),
        )

        self.assertEqual(result.status, CollectionStatus.PARTIAL_SUCCESS)
        self.assertEqual(len(result.articles), 1)
        self.assertEqual(
            result.articles[0].content_collection_status,
            "not_attempted",
        )
        self.assertEqual(
            result.articles[0].fallback_text_source,
            "provider_snippet",
        )
        self.assertEqual(result.failed_query_count, 1)
        self.assertEqual(provider.fetch_company_news.call_count, 2)
        self.assertEqual(result.metadata["metrics"]["request_failure_rate"], 0.5)
        self.assertIsNone(result.metadata["metrics"]["full_text_failure_rate"])
        self.assertIsNone(result.metadata["metrics"]["article_omission_rate"])

    def test_SEC_accepted_at_없이도_수집하고_나중_window를_계산할수있다(self) -> None:
        accepted_at = datetime.fromisoformat("2026-07-22T16:12:00-04:00")
        start_at, end_at = event_news_window(accepted_at)
        self.assertEqual(start_at, datetime(2026, 7, 21, 20, 12, tzinfo=UTC))
        self.assertEqual(end_at, datetime(2026, 7, 24, 20, 12, tzinfo=UTC))


class UniverseConfigTest(unittest.TestCase):
    def test_S_and_P_100_snapshot은_100회사_101ticker이다(self) -> None:
        config = NewsCollectionConfig.from_file(DEFAULT_CONFIG_PATH)
        universe = load_company_universe(config.universe_file)
        self.assertEqual(len(universe.companies), 100)
        self.assertEqual(sum(len(item.tickers) for item in universe.companies), 101)
        alphabet = next(
            item for item in universe.companies if item.company_key == "alphabet"
        )
        self.assertEqual(alphabet.tickers, ("GOOG", "GOOGL"))
        self.assertEqual(alphabet.cik, "0001652044")
        legacy_target = _temporary_target("GOOGL")
        self.assertEqual(legacy_target.company_key, "alphabet")
        self.assertEqual(legacy_target.tickers, ("GOOG", "GOOGL"))

        named_legacy_target = _temporary_target(
            "GOOGL",
            company_name="Google LLC",
        )
        self.assertEqual(named_legacy_target.company_key, "alphabet")
        self.assertEqual(named_legacy_target.cik, "0001652044")
        self.assertEqual(named_legacy_target.tickers, ("GOOG", "GOOGL"))
        self.assertIn("Google LLC", named_legacy_target.aliases)

        philip_morris = next(
            item
            for item in universe.companies
            if item.company_key == "philip-morris-international"
        )
        self.assertEqual(philip_morris.ambiguous_tickers, ("PM",))

        exxonmobil = next(
            item for item in universe.companies if item.company_key == "exxonmobil"
        )
        self.assertEqual(exxonmobil.cik, "0000034088")
        self.assertEqual(exxonmobil.legal_name, "Exxon Mobil Corporation")

    def test_universe_missing_required_company_field_is_value_error(self) -> None:
        payload = {
            "universe_name": "test",
            "universe_mode": "snapshot",
            "as_of": "2026-07-27",
            "source_url": "https://example.com",
            "companies": [
                {
                    "company_key": "apple",
                    "tickers": ["AAPL"],
                }
            ],
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            universe_path = Path(temp_dir) / "universe.json"
            universe_path.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaisesRegex(
                ValueError,
                r"companies\[0\].*legal_name",
            ):
                load_company_universe(universe_path)

    def test_cli_invalid_config_preserves_output_path(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_path = Path(temp_dir) / "result.json"
            exit_code = main(
                [
                    "--config",
                    str(Path(temp_dir) / "missing.json"),
                    "--output",
                    str(output_path),
                ]
            )
            self.assertEqual(exit_code, 4)
            payload = json.loads(output_path.read_text(encoding="utf-8"))
            self.assertEqual(payload["collection_outcome"], "invalid_config")

    def test_Google_RSS_CLI는_0시간과_5년조회요청을_거부한다(self) -> None:
        config = NewsCollectionConfig.from_file(DEFAULT_CONFIG_PATH)
        zero_hours = parse_args(["--tickers", "AAPL", "--lookback-hours", "0"])
        five_years = parse_args(
            [
                "--tickers",
                "AAPL",
                "--start",
                "2021-07-29T00:00:00Z",
                "--end",
                "2026-07-29T00:00:00Z",
            ]
        )
        with self.assertRaises(ValueError):
            _resolve_window(zero_hours, config)
        with self.assertRaises(ValueError):
            _resolve_window(five_years, config)


if __name__ == "__main__":
    unittest.main()
