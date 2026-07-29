from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import Enum
from typing import Protocol

from ..contracts import CompanyTarget, ProviderArticle


class ProviderFetchStatus(str, Enum):
    """공급자 호출 자체의 결과.

    정상적인 빈 피드와 공급자 장애를 분리하기 위해 collection 상태와 별도로
    유지합니다.
    """

    SUCCESS = "success"
    NO_RESULTS = "no_results"
    ERROR = "provider_error"


@dataclass(frozen=True)
class ProviderFetchResult:
    status: ProviderFetchStatus
    articles: tuple[ProviderArticle, ...] = ()
    failure_reason: str | None = None
    query_count: int = 0
    raw_item_count: int = 0
    invalid_item_count: int = 0
    feed_saturated: bool = False
    errors: tuple[str, ...] = ()
    oldest_item_published_at: datetime | None = None


class NewsProvider(Protocol):
    """뉴스 검색 공급자가 구현해야 하는 최소 인터페이스."""

    name: str

    def fetch_company_news(
        self,
        company: CompanyTarget,
        start_at: datetime,
        end_at: datetime,
        limit: int | None = None,
    ) -> ProviderFetchResult: ...
