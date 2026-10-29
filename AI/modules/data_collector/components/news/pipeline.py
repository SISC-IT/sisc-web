from __future__ import annotations

import time
from datetime import datetime, timezone
from typing import Callable, Iterable

from .contracts import (
    CollectedArticle,
    CollectionResult,
    CollectionStatus,
    CompanyTarget,
)
from .dedup import (
    deduplicate_exact,
    normalize_url,
    syndication_candidate_group_id,
)
from .providers.base import NewsProvider, ProviderFetchStatus
from .relevance import score_company_relevance
from .windows import require_aware_utc


Clock = Callable[[], datetime]
Sleeper = Callable[[float], None]


class CompanyNewsCollector:
    """SEC 입력 없이 issuer별 뉴스를 수집하는 one-shot 파이프라인."""

    def __init__(
        self,
        provider: NewsProvider,
        *,
        company_relevance_threshold: float = 0.60,
        inter_company_delay_seconds: float = 0.0,
        clock: Clock | None = None,
        sleeper: Sleeper | None = None,
    ) -> None:
        if not 0 <= company_relevance_threshold <= 1:
            raise ValueError("company_relevance_threshold는 0과 1 사이여야 합니다.")
        if inter_company_delay_seconds < 0:
            raise ValueError("inter_company_delay_seconds는 음수일 수 없습니다.")
        self.provider = provider
        self.company_relevance_threshold = company_relevance_threshold
        self.inter_company_delay_seconds = inter_company_delay_seconds
        self.clock = clock or (lambda: datetime.now(timezone.utc))
        self.sleeper = sleeper or time.sleep

    def collect_company(
        self,
        company: CompanyTarget,
        *,
        start_at: datetime,
        end_at: datetime,
        limit: int | None = None,
    ) -> CollectionResult:
        started_at = require_aware_utc(self.clock(), "clock")
        start_utc = require_aware_utc(start_at, "start_at")
        end_utc = require_aware_utc(end_at, "end_at")
        if start_utc > end_utc:
            raise ValueError("start_at은 end_at보다 늦을 수 없습니다.")
        if limit is not None and limit <= 0:
            raise ValueError("limit은 0보다 커야 합니다.")

        provider_result = self.provider.fetch_company_news(
            company,
            start_at=start_utc,
            end_at=end_utc,
            # 관련도 판정 전에 feed를 자르면 뒤쪽 관련 기사를 놓칠 수 있습니다.
            # RSS payload/item 상한은 provider가 별도로 방어합니다.
            limit=None,
        )
        completed_at = require_aware_utc(self.clock(), "clock")
        base_metadata = {
            "company_key": company.company_key,
            "cik": company.cik,
            "tickers": list(company.tickers),
            "requested_window": {
                "start_at_utc": start_utc.isoformat().replace("+00:00", "Z"),
                "end_at_utc": end_utc.isoformat().replace("+00:00", "Z"),
            },
            "provider": self.provider.name,
            "coverage_status": "best_effort",
            "supports_complete_historical_range": False,
            "raw_item_count": provider_result.raw_item_count,
            "invalid_item_count": provider_result.invalid_item_count,
            "feed_saturated": provider_result.feed_saturated,
            "oldest_item_published_at_utc": (
                provider_result.oldest_item_published_at.isoformat().replace(
                    "+00:00", "Z"
                )
                if provider_result.oldest_item_published_at
                else None
            ),
            # provider snippet/title은 보존하되 이 단계에서 새 요약을 만들지 않습니다.
            "text_fallback_policy": "provider_snippet_then_title",
            "full_text_attempt_count": 0,
            "full_text_failure_rate": None,
            "article_omission_rate": None,
            "article_omission_rate_reason": "gold_corpus_unavailable",
        }

        if provider_result.status == ProviderFetchStatus.ERROR:
            return CollectionResult(
                status=CollectionStatus.PROVIDER_ERROR,
                failure_reason=provider_result.failure_reason or "provider_error",
                errors=provider_result.errors,
                query_count=provider_result.query_count,
                failed_query_count=provider_result.query_count,
                started_at_utc=started_at,
                completed_at_utc=completed_at,
                metadata=base_metadata,
            )

        if provider_result.status == ProviderFetchStatus.NO_RESULTS:
            return CollectionResult(
                status=CollectionStatus.NO_RESULTS,
                query_count=provider_result.query_count,
                successful_query_count=provider_result.query_count,
                started_at_utc=started_at,
                completed_at_utc=completed_at,
                metadata=base_metadata,
            )

        deduplicated = deduplicate_exact(provider_result.articles)
        collected: list[CollectedArticle] = []
        unrelated_count = 0
        for article, identity in zip(
            deduplicated.articles,
            deduplicated.identities,
        ):
            relevance = score_company_relevance(article, company)
            if not relevance.is_relevant(self.company_relevance_threshold):
                unrelated_count += 1
                continue

            collected.append(
                CollectedArticle(
                    company_key=company.company_key,
                    cik=company.cik,
                    tickers=company.tickers,
                    provider=article.provider,
                    provider_article_id=article.provider_article_id,
                    title=article.title,
                    provider_url=article.provider_url,
                    normalized_url=normalize_url(article.provider_url),
                    published_at_raw=article.published_at_raw,
                    published_at_utc=article.published_at_utc,
                    retrieved_at_utc=completed_at,
                    source=article.source,
                    snippet=article.snippet,
                    company_relevance_score=relevance.score,
                    company_relevance_reasons=relevance.reasons,
                    company_relevance_version=relevance.version,
                    matched_ticker=relevance.matched_ticker,
                    exact_identity_key=identity.key,
                    exact_identity_method=identity.method.value,
                    syndication_candidate_group_id=syndication_candidate_group_id(
                        article
                    ),
                    fallback_text=article.snippet or article.title,
                    fallback_text_source=(
                        "provider_snippet" if article.snippet else "title"
                    ),
                )
            )

        relevant_before_limit_count = len(collected)
        if limit is not None:
            collected = collected[:limit]
        base_metadata.update(
            {
                "exact_duplicate_count": deduplicated.duplicate_count,
                "unique_article_count": len(deduplicated.articles),
                "unrelated_article_count": unrelated_count,
                "relevant_before_limit_count": relevant_before_limit_count,
                "output_truncated_count": relevant_before_limit_count - len(collected),
                "company_relevance_threshold": self.company_relevance_threshold,
            }
        )
        status = (
            CollectionStatus.SUCCESS if collected else CollectionStatus.NO_RELEVANT_NEWS
        )
        return CollectionResult(
            status=status,
            articles=tuple(collected),
            query_count=provider_result.query_count,
            successful_query_count=provider_result.query_count,
            started_at_utc=started_at,
            completed_at_utc=completed_at,
            metadata=base_metadata,
        )

    def collect_batch(
        self,
        companies: Iterable[CompanyTarget],
        *,
        start_at: datetime,
        end_at: datetime,
        limit_per_company: int | None = None,
    ) -> CollectionResult:
        started_at = require_aware_utc(self.clock(), "clock")
        enabled_companies = [company for company in companies if company.enabled]
        if not enabled_companies:
            completed_at = require_aware_utc(self.clock(), "clock")
            return CollectionResult(
                status=CollectionStatus.INVALID_CONFIG,
                failure_reason="no_enabled_companies",
                started_at_utc=started_at,
                completed_at_utc=completed_at,
            )

        articles: list[CollectedArticle] = []
        errors: list[str] = []
        summaries: list[dict] = []
        query_count = 0
        successful_query_count = 0
        failed_query_count = 0
        statuses: list[CollectionStatus] = []
        raw_item_count = 0
        invalid_item_count = 0
        exact_duplicate_count = 0
        unrelated_article_count = 0
        saturated_company_count = 0

        for index, company in enumerate(enabled_companies):
            if index and self.inter_company_delay_seconds:
                self.sleeper(self.inter_company_delay_seconds)
            try:
                result = self.collect_company(
                    company,
                    start_at=start_at,
                    end_at=end_at,
                    limit=limit_per_company,
                )
            except Exception as exc:
                # 한 issuer의 설정/파싱 문제가 전체 S&P 100 실행을 중단시키지 않게 합니다.
                result = CollectionResult(
                    status=CollectionStatus.PROVIDER_ERROR,
                    failure_reason="company_collection_exception",
                    errors=(f"{company.company_key}: {type(exc).__name__}: {exc}",),
                    failed_query_count=0,
                    metadata={"company_key": company.company_key},
                )

            statuses.append(result.status)
            articles.extend(result.articles)
            errors.extend(result.errors)
            query_count += result.query_count
            successful_query_count += result.successful_query_count
            failed_query_count += result.failed_query_count
            raw_item_count += int(result.metadata.get("raw_item_count", 0))
            invalid_item_count += int(result.metadata.get("invalid_item_count", 0))
            exact_duplicate_count += int(
                result.metadata.get("exact_duplicate_count", 0)
            )
            unrelated_article_count += int(
                result.metadata.get("unrelated_article_count", 0)
            )
            saturated_company_count += int(bool(result.metadata.get("feed_saturated")))
            summaries.append(
                {
                    "company_key": company.company_key,
                    "status": result.status.value,
                    "relevant_article_count": result.relevant_article_count,
                    "failure_reason": result.failure_reason,
                    "raw_item_count": result.metadata.get("raw_item_count", 0),
                    "invalid_item_count": result.metadata.get("invalid_item_count", 0),
                }
            )

        failed_company_count = sum(
            status in (CollectionStatus.PROVIDER_ERROR, CollectionStatus.INVALID_CONFIG)
            for status in statuses
        )
        if failed_company_count == len(statuses):
            status = CollectionStatus.PROVIDER_ERROR
            failure_reason = "all_companies_failed"
        elif failed_company_count:
            status = CollectionStatus.PARTIAL_SUCCESS
            failure_reason = "some_companies_failed"
        elif articles:
            status = CollectionStatus.SUCCESS
            failure_reason = None
        elif all(item == CollectionStatus.NO_RESULTS for item in statuses):
            status = CollectionStatus.NO_RESULTS
            failure_reason = None
        else:
            status = CollectionStatus.NO_RELEVANT_NEWS
            failure_reason = None

        completed_at = require_aware_utc(self.clock(), "clock")
        return CollectionResult(
            status=status,
            articles=tuple(articles),
            failure_reason=failure_reason,
            errors=tuple(errors),
            query_count=query_count,
            successful_query_count=successful_query_count,
            failed_query_count=failed_query_count,
            started_at_utc=started_at,
            completed_at_utc=completed_at,
            metadata={
                "provider": self.provider.name,
                "company_count": len(enabled_companies),
                "failed_company_count": failed_company_count,
                "coverage_status": "best_effort",
                "supports_complete_historical_range": False,
                "company_results": summaries,
                "metrics": {
                    "request_failure_rate": (
                        failed_query_count / query_count if query_count else None
                    ),
                    "provider_item_count": raw_item_count,
                    "invalid_item_count": invalid_item_count,
                    "invalid_item_rate": (
                        invalid_item_count / raw_item_count if raw_item_count else 0.0
                    ),
                    "exact_duplicate_count": exact_duplicate_count,
                    "unrelated_article_count": unrelated_article_count,
                    "full_text_attempt_count": 0,
                    "full_text_failure_rate": None,
                    "article_omission_rate": None,
                    "article_omission_rate_reason": "gold_corpus_unavailable",
                    "feed_saturated_company_count": saturated_company_count,
                },
            },
        )
