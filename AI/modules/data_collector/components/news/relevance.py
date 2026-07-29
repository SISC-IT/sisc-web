"""Deterministic company-level relevance scoring.

This module does not score relevance to an SEC filing or other event.  Event
relevance requires event text and timestamps and belongs to the later linking
stage.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from typing import Iterable, Optional, Tuple

from .contracts import CompanyTarget, ProviderArticle, SerializableContract


COMPANY_RELEVANCE_VERSION = "company-relevance-v1"
DEFAULT_RELEVANCE_THRESHOLD = 0.60

_CORPORATE_SUFFIXES = (
    "incorporated",
    "corporation",
    "company",
    "limited",
    "holdings",
    "holding",
    "group",
    "inc",
    "corp",
    "co",
    "ltd",
    "plc",
)
_MARKET_CONTEXT_TERMS = (
    "acquisition",
    "acquire",
    "antitrust",
    "business",
    "ceo",
    "cfo",
    "chief executive",
    "company",
    "demand",
    "dividend",
    "stock",
    "stocks",
    "share",
    "shares",
    "market cap",
    "valuation",
    "nasdaq",
    "nyse",
    "earnings",
    "revenue",
    "sales",
    "profit",
    "guidance",
    "forecast",
    "outlook",
    "quarter",
    "production",
    "supply",
    "launch",
    "expands",
    "expansion",
    "services",
    "regulator",
    "regulation",
    "lawsuit",
    "settlement",
    "layoff",
    "workforce",
    "executive",
    "merger",
    "investment",
    "financial",
    "results",
    "tariff",
    "recall",
    "outage",
    "investor",
)


@dataclass(frozen=True)
class RelevanceResult(SerializableContract):
    score: float
    reasons: Tuple[str, ...]
    version: str = COMPANY_RELEVANCE_VERSION
    matched_ticker: Optional[str] = None

    def __post_init__(self) -> None:
        score = float(self.score)
        if not 0.0 <= score <= 1.0:
            raise ValueError("score must be between 0 and 1")
        object.__setattr__(self, "score", round(score, 6))
        object.__setattr__(self, "reasons", tuple(dict.fromkeys(self.reasons)))
        if self.matched_ticker is not None:
            object.__setattr__(
                self, "matched_ticker", self.matched_ticker.strip().upper()
            )

    def is_relevant(self, threshold: float = DEFAULT_RELEVANCE_THRESHOLD) -> bool:
        if not 0.0 <= threshold <= 1.0:
            raise ValueError("threshold must be between 0 and 1")
        return self.score >= threshold


def _normalized_words(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).casefold()
    value = re.sub(r"[^\w]+", " ", value, flags=re.UNICODE)
    return " ".join(value.split())


def _phrase_present(normalized_text: str, phrase: str) -> bool:
    normalized_phrase = _normalized_words(phrase)
    if not normalized_phrase:
        return False
    return bool(
        re.search(
            rf"(?<!\w){re.escape(normalized_phrase)}(?!\w)",
            normalized_text,
        )
    )


def _legal_name_candidates(legal_name: str) -> Tuple[str, ...]:
    normalized = _normalized_words(legal_name)
    if not normalized:
        return ()

    candidates = [normalized]
    words = normalized.split()
    while words and words[-1] in _CORPORATE_SUFFIXES:
        words.pop()
    without_suffix = " ".join(words)
    if len(without_suffix) >= 4 and without_suffix != normalized:
        candidates.append(without_suffix)
    return tuple(dict.fromkeys(candidates))


def _ticker_matches(text: str, ticker: str) -> Tuple[bool, bool]:
    """Return ``(decorated_match, bare_token_match)`` for a ticker."""

    escaped = re.escape(ticker.upper())
    uppercase_text = unicodedata.normalize("NFKC", text).upper()
    decorated_patterns = (
        rf"\${escaped}(?![A-Z0-9])",
        rf"\({escaped}\)",
        rf"\b(?:NASDAQ|NYSE|NYSEARCA|AMEX)\s*:\s*{escaped}\b",
    )
    decorated = any(
        re.search(pattern, uppercase_text) for pattern in decorated_patterns
    )
    bare = bool(
        re.search(
            rf"(?<![A-Z0-9]){escaped}(?![A-Z0-9])",
            uppercase_text,
        )
    )
    return decorated, bare


def _uppercase_ticker_token_matches(text: str, ticker: str) -> bool:
    normalized = unicodedata.normalize("NFKC", text)
    escaped = re.escape(ticker.upper())
    return bool(re.search(rf"(?<![A-Z0-9]){escaped}(?![A-Z0-9])", normalized))


def _best_name_evidence(
    title: str,
    snippet: str,
    target: CompanyTarget,
) -> Tuple[float, Tuple[str, ...]]:
    normalized_title = _normalized_words(title)
    normalized_snippet = _normalized_words(snippet)
    evidence = []
    score = 0.0

    if any(
        _phrase_present(normalized_title, candidate)
        for candidate in _legal_name_candidates(target.legal_name)
    ):
        score = max(score, 0.55)
        evidence.append("legal_name:title")
    elif any(
        _phrase_present(normalized_snippet, candidate)
        for candidate in _legal_name_candidates(target.legal_name)
    ):
        score = max(score, 0.45)
        evidence.append("legal_name:snippet")

    for alias in target.aliases:
        if _phrase_present(normalized_title, alias):
            score = max(score, 0.50)
            evidence.append(f"alias:title:{_normalized_words(alias)}")
        elif _phrase_present(normalized_snippet, alias):
            score = max(score, 0.40)
            evidence.append(f"alias:snippet:{_normalized_words(alias)}")

    return score, tuple(evidence)


def _contains_market_context(values: Iterable[str]) -> bool:
    normalized = _normalized_words(" ".join(values))
    return any(_phrase_present(normalized, term) for term in _MARKET_CONTEXT_TERMS)


def score_company_relevance(
    article: ProviderArticle,
    target: CompanyTarget,
) -> RelevanceResult:
    """Score whether an article concerns an issuer, independent of any event.

    The score is a versioned heuristic, not a calibrated probability.  An
    ambiguous ticker (for example ``A`` or ``ON``) never establishes relevance
    by itself, even when it appears in a decorated market form.
    """

    snippet = article.snippet or ""
    name_score, name_reasons = _best_name_evidence(article.title, snippet, target)
    reasons = list(name_reasons)
    score = name_score
    matched_ticker = None
    # One- and two-character symbols overlap too often with normal prose
    # (A, C, F, T, AI, ON, ...).  Treat them as ambiguous even if a config
    # author forgot to list them explicitly.
    ambiguous = set(target.ambiguous_tickers)
    ambiguous.update(ticker for ticker in target.tickers if len(ticker) <= 2)

    best_ticker_score = 0.0
    best_ticker_reason = None
    best_ticker = None
    for ticker in target.tickers:
        title_decorated, title_bare = _ticker_matches(article.title, ticker)
        snippet_decorated, snippet_bare = _ticker_matches(snippet, ticker)

        if ticker in ambiguous:
            if name_score > 0.0 and (
                title_decorated or title_bare or snippet_decorated or snippet_bare
            ):
                reasons.append(f"ambiguous_ticker:corroborated:{ticker}")
                matched_ticker = matched_ticker or ticker
            elif (
                len(ticker) >= 3
                and _contains_market_context((article.title, snippet))
                and (
                    _uppercase_ticker_token_matches(article.title, ticker)
                    or _uppercase_ticker_token_matches(snippet, ticker)
                )
            ):
                # CAT/LOW/NOW 같은 사전 단어형 ticker는 대문자 토큰과
                # 금융·사업 문맥이 함께 있을 때만 제한적으로 인정합니다.
                if 0.60 > best_ticker_score:
                    best_ticker_score = 0.60
                    best_ticker_reason = (
                        f"ambiguous_ticker:uppercase_business_context:{ticker}"
                    )
                    best_ticker = ticker
            continue

        ticker_score = 0.0
        ticker_reason = None
        if title_decorated:
            ticker_score = 0.55
            ticker_reason = f"ticker_decorated:title:{ticker}"
        elif title_bare:
            ticker_score = 0.50
            ticker_reason = f"ticker:title:{ticker}"
        elif snippet_decorated:
            ticker_score = 0.40
            ticker_reason = f"ticker_decorated:snippet:{ticker}"
        elif snippet_bare:
            ticker_score = 0.30
            ticker_reason = f"ticker:snippet:{ticker}"

        if ticker_score > best_ticker_score:
            best_ticker_score = ticker_score
            best_ticker_reason = ticker_reason
            best_ticker = ticker

    if best_ticker_reason is not None:
        reasons.append(best_ticker_reason)
        matched_ticker = matched_ticker or best_ticker

    score = max(score, best_ticker_score)
    if name_score > 0.0 and best_ticker_score > 0.0:
        score = min(1.0, score + 0.15)
        reasons.append("name_and_ticker:corroborated")

    if score > 0.0 and _contains_market_context((article.title, snippet)):
        score = min(1.0, score + 0.20)
        reasons.append("business_or_market_context")

    return RelevanceResult(
        score=score,
        reasons=tuple(reasons),
        matched_ticker=matched_ticker,
    )


def is_company_relevant(
    article: ProviderArticle,
    target: CompanyTarget,
    threshold: float = DEFAULT_RELEVANCE_THRESHOLD,
) -> bool:
    return score_company_relevance(article, target).is_relevant(threshold)
