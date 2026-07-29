"""Exact deduplication and conservative syndication-candidate grouping."""

from __future__ import annotations

import hashlib
import html
import re
import unicodedata
from dataclasses import dataclass
from datetime import timezone
from enum import Enum
from typing import Dict, Iterable, List, Mapping, Optional, Sequence, Tuple
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from .contracts import ProviderArticle, SerializableContract


SYNDICATION_GROUP_VERSION = "syndication-candidate-v1"
SYNDICATION_BUCKET_HOURS = 6

_TRACKING_PARAMETER_NAMES = frozenset(
    {
        "_hsenc",
        "_hsmi",
        "dclid",
        "fbclid",
        "gclid",
        "igshid",
        "mc_cid",
        "mc_eid",
        "mkt_tok",
        "msclkid",
        "oly_anon_id",
        "oly_enc_id",
        "rb_clickid",
        "s_cid",
        "vero_conv",
        "vero_id",
    }
)


class ExactIdentityMethod(str, Enum):
    PROVIDER_ARTICLE_ID = "provider_article_id"
    NORMALIZED_URL = "normalized_url"
    PROVIDER_URL_HASH = "provider_url_hash"


@dataclass(frozen=True)
class ExactIdentity(SerializableContract):
    key: str
    method: ExactIdentityMethod


@dataclass(frozen=True)
class ExactDeduplicationResult(SerializableContract):
    """Stable-order exact deduplication output.

    ``identities`` is aligned with ``articles``.  Title similarity is not used
    to remove any row.
    """

    articles: Tuple[ProviderArticle, ...]
    identities: Tuple[ExactIdentity, ...]
    duplicate_count: int

    def __post_init__(self) -> None:
        if len(self.articles) != len(self.identities):
            raise ValueError("articles and identities must have equal lengths")
        if self.duplicate_count < 0:
            raise ValueError("duplicate_count must not be negative")


def _is_tracking_parameter(name: str) -> bool:
    lowered = name.casefold()
    return lowered.startswith("utm_") or lowered in _TRACKING_PARAMETER_NAMES


def normalize_url(url: str) -> Optional[str]:
    """Normalize an HTTP(S) URL without deleting content-significant params."""

    candidate = url.strip()
    if not candidate:
        return None

    try:
        parsed = urlsplit(candidate)
        scheme = parsed.scheme.casefold()
        if scheme not in ("http", "https") or parsed.hostname is None:
            return None
        if parsed.username is not None or parsed.password is not None:
            return None

        hostname = parsed.hostname.encode("idna").decode("ascii").casefold()
        if ":" in hostname and not hostname.startswith("["):
            hostname = f"[{hostname}]"

        try:
            port = parsed.port
        except ValueError:
            return None
        default_port = (scheme == "http" and port == 80) or (
            scheme == "https" and port == 443
        )
        netloc = hostname
        if port is not None and not default_port:
            netloc = f"{hostname}:{port}"

        query_items = [
            (key, value)
            for key, value in parse_qsl(
                parsed.query, keep_blank_values=True, strict_parsing=False
            )
            if not _is_tracking_parameter(key)
        ]
        query_items.sort(key=lambda item: (item[0], item[1]))
        normalized_query = urlencode(query_items, doseq=True)
        path = parsed.path or "/"
        return urlunsplit((scheme, netloc, path, normalized_query, ""))
    except (UnicodeError, ValueError):
        return None


def _digest(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def exact_identity_candidates(
    article: ProviderArticle,
) -> Tuple[ExactIdentity, ...]:
    """Return all strong identities in priority order.

    Recording all aliases lets a provider ID match one run while the normalized
    URL matches a later run.  The first element remains the canonical identity.
    """

    candidates: List[ExactIdentity] = []
    provider = article.provider.strip().casefold()
    if article.provider_article_id:
        candidates.append(
            ExactIdentity(
                key="provider-id:"
                + _digest(f"{provider}\0{article.provider_article_id.strip()}"),
                method=ExactIdentityMethod.PROVIDER_ARTICLE_ID,
            )
        )

    normalized = normalize_url(article.provider_url)
    if normalized is not None:
        candidates.append(
            ExactIdentity(
                key="normalized-url:" + _digest(normalized),
                method=ExactIdentityMethod.NORMALIZED_URL,
            )
        )

    candidates.append(
        ExactIdentity(
            key="provider-url:"
            + _digest(f"{provider}\0{article.provider_url.strip()}"),
            method=ExactIdentityMethod.PROVIDER_URL_HASH,
        )
    )
    return tuple(candidates)


def build_exact_identity(article: ProviderArticle) -> ExactIdentity:
    """Return the highest-priority exact identity for an article."""

    return exact_identity_candidates(article)[0]


def deduplicate_exact(
    articles: Iterable[ProviderArticle],
) -> ExactDeduplicationResult:
    """Remove exact duplicates while retaining the first occurrence."""

    unique_articles: List[ProviderArticle] = []
    identities: List[ExactIdentity] = []
    seen_keys = set()
    duplicate_count = 0

    for article in articles:
        candidates = exact_identity_candidates(article)
        if any(candidate.key in seen_keys for candidate in candidates):
            duplicate_count += 1
            # 중복 행에 새로 나타난 강한 alias도 기억해야 이후 실행/항목에서
            # provider ID ↔ URL 연결이 전이적으로 유지됩니다.
            seen_keys.update(candidate.key for candidate in candidates)
            continue

        unique_articles.append(article)
        identities.append(candidates[0])
        seen_keys.update(candidate.key for candidate in candidates)

    return ExactDeduplicationResult(
        articles=tuple(unique_articles),
        identities=tuple(identities),
        duplicate_count=duplicate_count,
    )


def normalize_title(title: str) -> str:
    """Normalize a title solely for conservative candidate grouping."""

    value = html.unescape(unicodedata.normalize("NFKC", title)).casefold()
    value = re.sub(r"[^\w]+", " ", value, flags=re.UNICODE)
    return " ".join(value.split())


def syndication_candidate_group_id(
    article: ProviderArticle,
    bucket_hours: int = SYNDICATION_BUCKET_HOURS,
) -> str:
    """Build a candidate group from normalized title and a UTC time bucket.

    Membership is a hint for downstream review/linking.  It is deliberately not
    an exact-duplicate key and must not be used to delete article rows.
    """

    if bucket_hours <= 0:
        raise ValueError("bucket_hours must be positive")
    published = article.published_at_utc.astimezone(timezone.utc)
    bucket_seconds = bucket_hours * 60 * 60
    bucket = int(published.timestamp()) // bucket_seconds
    candidate_title = article.title
    if article.source:
        # Google RSS 제목 끝의 " - Publisher"는 같은 원문이 다른 매체에
        # 재전송될 때 달라지므로 후보 그룹 계산에서만 제거합니다.
        candidate_title = re.sub(
            rf"\s+[-–—|]\s+{re.escape(article.source)}\s*$",
            "",
            candidate_title,
            flags=re.IGNORECASE,
        )
    payload = f"{normalize_title(candidate_title)}\0{bucket_hours}\0{bucket}"
    return f"{SYNDICATION_GROUP_VERSION}:{_digest(payload)[:24]}"


def group_syndication_candidates(
    articles: Sequence[ProviderArticle],
    bucket_hours: int = SYNDICATION_BUCKET_HOURS,
) -> Mapping[str, Tuple[ProviderArticle, ...]]:
    """Group candidates without removing or merging any article."""

    grouped: Dict[str, List[ProviderArticle]] = {}
    for article in articles:
        group_id = syndication_candidate_group_id(article, bucket_hours)
        grouped.setdefault(group_id, []).append(article)
    return {
        group_id: tuple(group_articles) for group_id, group_articles in grouped.items()
    }
