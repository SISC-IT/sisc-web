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
from .repository import CollectionAlreadyRunningError, NewsRepository
from .event_linker import (
    EVENT_RELEVANCE_VERSION,
    EventArticleCandidate,
    EventNewsRepository,
    EventRelationship,
    EventRelevanceResult,
    SecEvent,
    score_event_relevance,
)
from .windows import event_news_window, forward_collection_window

__all__ = [
    "COMPANY_RELEVANCE_VERSION",
    "DEFAULT_RELEVANCE_THRESHOLD",
    "EVENT_RELEVANCE_VERSION",
    "SYNDICATION_BUCKET_HOURS",
    "SYNDICATION_GROUP_VERSION",
    "DEFAULT_CONFIG_PATH",
    "CollectedArticle",
    "CollectionResult",
    "CollectionStatus",
    "CompanyTarget",
    "CompanyUniverse",
    "CompanyNewsCollector",
    "CollectionAlreadyRunningError",
    "EventArticleCandidate",
    "EventNewsRepository",
    "EventRelationship",
    "EventRelevanceResult",
    "ExactDeduplicationResult",
    "ExactIdentity",
    "ExactIdentityMethod",
    "ProviderArticle",
    "NewsCollectionConfig",
    "NewsRepository",
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
    "score_event_relevance",
    "SecEvent",
    "syndication_candidate_group_id",
    "to_serializable",
]
