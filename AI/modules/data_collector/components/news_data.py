"""미국 기업 뉴스 수집 호환 façade.

실제 provider/파싱/중복/관련도 로직은 ``components.news`` 패키지에 있습니다.
이 모듈은 기존 import 경로를 유지하되, 뉴스 담당 범위가 아닌 LLM 요약과
안전하지 않은 임의 URL 본문 스크래핑은 수행하지 않습니다.
"""

from __future__ import annotations

# ruff: noqa: E402

from datetime import datetime, timedelta, timezone
from pathlib import Path
import sys
from typing import Any

import requests


PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.append(str(PROJECT_ROOT))

from AI.modules.data_collector.components.news.contracts import CompanyTarget
from AI.modules.data_collector.components.news.config import (
    DEFAULT_CONFIG_PATH,
    NewsCollectionConfig,
    load_company_universe,
)
from AI.modules.data_collector.components.news.pipeline import CompanyNewsCollector
from AI.modules.data_collector.components.news.providers.base import ProviderFetchStatus
from AI.modules.data_collector.components.news.providers.google_news_rss import (
    GoogleNewsRssProvider,
)


AMBIGUOUS_TICKERS = frozenset({"A", "AI", "ON"})


class NewsCollectionError(RuntimeError):
    """정상 0건과 구분되어야 하는 provider 수집 실패."""


def _temporary_target(
    ticker: str,
    *,
    company_name: str | None = None,
    aliases: tuple[str, ...] = (),
) -> CompanyTarget:
    symbol = ticker.strip().upper()
    if not symbol:
        raise ValueError("ticker는 비어 있을 수 없습니다.")
    if company_name is None:
        config = NewsCollectionConfig.from_file(DEFAULT_CONFIG_PATH)
        universe = load_company_universe(config.universe_file)
        for company in universe.companies:
            if symbol in company.tickers:
                if not aliases:
                    return company
                return CompanyTarget(
                    company_key=company.company_key,
                    cik=company.cik,
                    legal_name=company.legal_name,
                    tickers=company.tickers,
                    aliases=(*company.aliases, *aliases),
                    enabled=company.enabled,
                    ambiguous_tickers=company.ambiguous_tickers,
                )
    return CompanyTarget(
        company_key=symbol.casefold(),
        cik=None,
        legal_name=company_name or symbol,
        tickers=(symbol,),
        aliases=aliases,
        ambiguous_tickers=(symbol,) if symbol in AMBIGUOUS_TICKERS else (),
    )


def fetch_news_links(
    ticker: str,
    limit: int = 3,
    *,
    company_name: str | None = None,
    aliases: tuple[str, ...] = (),
    session: requests.Session | None = None,
) -> list[dict[str, Any]]:
    """최근 72시간 Google RSS 메타데이터를 가져오는 호환 함수.

    공급자 장애를 빈 목록으로 숨기지 않고 ``NewsCollectionError``로 올립니다.
    빈 목록은 피드 호출은 성공했지만 조회 구간에 기사가 없다는 뜻입니다.
    """

    if limit <= 0:
        raise ValueError("limit은 0보다 커야 합니다.")
    target = _temporary_target(
        ticker,
        company_name=company_name,
        aliases=aliases,
    )
    end_at = datetime.now(timezone.utc)
    provider = GoogleNewsRssProvider(session=session)
    result = provider.fetch_company_news(
        target,
        start_at=end_at - timedelta(hours=72),
        end_at=end_at,
        limit=limit,
    )
    if result.status == ProviderFetchStatus.ERROR:
        raise NewsCollectionError("; ".join(result.errors) or "Google RSS 수집 실패")

    return [
        {
            "title": article.title,
            "link": article.provider_url,
            "pubDate": article.published_at_raw,
            "published_at_utc": article.published_at_utc.isoformat(),
            "source": article.source,
            "provider": article.provider,
            "provider_article_id": article.provider_article_id,
            # provider가 준 snippet이며 AI가 생성한 요약이 아닙니다.
            "snippet": article.snippet,
        }
        for article in result.articles
    ]


def collect_news(
    ticker: str,
    *,
    company_name: str | None = None,
    aliases: tuple[str, ...] = (),
    limit: int = 20,
    lookback_hours: int = 2,
    session: requests.Session | None = None,
) -> dict[str, Any]:
    """SEC 이벤트 없이 기업 관련 뉴스를 수집하고 명시적 상태를 반환합니다."""

    if lookback_hours <= 0 or lookback_hours > 72:
        raise ValueError("lookback_hours는 1~72시간이어야 합니다.")
    target = _temporary_target(
        ticker,
        company_name=company_name,
        aliases=aliases,
    )
    end_at = datetime.now(timezone.utc)
    collector = CompanyNewsCollector(
        GoogleNewsRssProvider(session=session),
        # ticker만 받은 legacy 호출도 동작하되, 모호 ticker는 회사명이 필요합니다.
        company_relevance_threshold=0.60,
    )
    result = collector.collect_company(
        target,
        start_at=end_at - timedelta(hours=lookback_hours),
        end_at=end_at,
        limit=limit,
    )
    return result.to_dict()


if __name__ == "__main__":
    import json

    print(json.dumps(collect_news("AAPL", company_name="Apple Inc."), indent=2))
