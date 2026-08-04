"""뉴스 공급자 구현체.

공급자별 응답 형식은 이 패키지 안에서 표준 ``ProviderArticle`` 계약으로
변환합니다. Infomax는 API 계약이 확정된 뒤 같은 인터페이스로 추가합니다.
"""

from .base import NewsProvider, ProviderFetchResult, ProviderFetchStatus
from .google_news_rss import GoogleNewsRssProvider

__all__ = [
    "GoogleNewsRssProvider",
    "NewsProvider",
    "ProviderFetchResult",
    "ProviderFetchStatus",
]
