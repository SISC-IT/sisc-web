"""Independent company-news collection core."""

from .contracts import (
    CollectedArticle,
    CollectionResult,
    CollectionStatus,
    CompanyTarget,
    ProviderArticle,
    SerializableContract,
    to_serializable,
)
from .config import (
    DEFAULT_CONFIG_PATH,
    CompanyUniverse,
    NewsCollectionConfig,
    load_company_universe,
)
from .dedup import (
    SYNDICATION_BUCKET_HOURS,
    SYNDICATION_GROUP_VERSION,
    ExactDeduplicationResult,
    ExactIdentity,
    ExactIdentityMethod,
    build_exact_identity,
    deduplicate_exact,
    exact_identity_candidates,
    group_syndication_candidates,
    normalize_title,
    normalize_url,
    syndication_candidate_group_id,
)
from .relevance import (
    COMPANY_RELEVANCE_VERSION,
    DEFAULT_RELEVANCE_THRESHOLD,
    RelevanceResult,
    is_company_relevant,
    score_company_relevance,
)
from .pipeline import CompanyNewsCollector
from .windows import event_news_window, forward_collection_window

__all__ = [
    "COMPANY_RELEVANCE_VERSION",
    "DEFAULT_RELEVANCE_THRESHOLD",
    "SYNDICATION_BUCKET_HOURS",
    "SYNDICATION_GROUP_VERSION",
    "DEFAULT_CONFIG_PATH",
    "CollectedArticle",
    "CollectionResult",
    "CollectionStatus",
    "CompanyTarget",
    "CompanyUniverse",
    "CompanyNewsCollector",
    "ExactDeduplicationResult",
    "ExactIdentity",
    "ExactIdentityMethod",
    "ProviderArticle",
    "NewsCollectionConfig",
    "RelevanceResult",
    "SerializableContract",
    "build_exact_identity",
    "deduplicate_exact",
    "exact_identity_candidates",
    "group_syndication_candidates",
    "event_news_window",
    "forward_collection_window",
    "is_company_relevant",
    "load_company_universe",
    "normalize_title",
    "normalize_url",
    "score_company_relevance",
    "syndication_candidate_group_id",
    "to_serializable",
]
