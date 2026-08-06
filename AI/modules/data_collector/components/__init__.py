"""데이터 수집 component의 지연 import 진입점.

이전에는 이 패키지의 하위 모듈 하나만 import해도 yfinance, DB 등 모든
수집기 의존성을 즉시 불러왔습니다. 독립 뉴스 수집기가 다른 수집기의 선택
의존성 때문에 실행되지 않는 일을 막기 위해 기존 public 이름은 유지하면서
필요할 때만 해당 모듈을 import합니다.
"""

from __future__ import annotations

from importlib import import_module
from typing import Any


_EXPORTS = {
    "MarketDataCollector": (".market_data", "MarketDataCollector"),
    "StockInfoCollector": (".stock_info_collector", "StockInfoCollector"),
    "FundamentalsDataCollector": (
        ".company_fundamentals_data",
        "FundamentalsDataCollector",
    ),
    "MacroDataCollector": (".macro_data", "MacroDataCollector"),
    "CryptoDataCollector": (".crypto_data", "CryptoDataCollector"),
    "EventDataCollector": (".event_data", "EventDataCollector"),
    "MarketBreadthCollector": (".market_breadth_data", "MarketBreadthCollector"),
    "MarketBreadthStatsCollector": (
        ".market_breadth_stats",
        "MarketBreadthStatsCollector",
    ),
    "TickerUpdater": (".ticker_updater", "TickerUpdater"),
    "CompanyNameKoreanUpdater": (
        ".company_name_korean_updater",
        "CompanyNameKoreanUpdater",
    ),
    "KoreaStockCollectorConfig": (
        ".korea_stock_data",
        "KoreaStockCollectorConfig",
    ),
    "KoreaStockDataCollector": (
        ".korea_stock_data",
        "KoreaStockDataCollector",
    ),
    "SecEdgarCollectorConfig": (
        ".sec_edgar_data",
        "SecEdgarCollectorConfig",
    ),
    "SecEdgarDataCollector": (
        ".sec_edgar_data",
        "SecEdgarDataCollector",
    ),
    "SecFilingFileQuery": (
        ".sec_edgar_query",
        "SecFilingFileQuery",
    ),
    "create_sec_filing_query": (
        ".sec_edgar_query",
        "create_sec_filing_query",
    ),
}

__all__ = list(_EXPORTS)


def __getattr__(name: str) -> Any:
    try:
        module_name, attribute_name = _EXPORTS[name]
    except KeyError as exc:
        raise AttributeError(f"module {__name__!r} has no attribute {name!r}") from exc
    value = getattr(import_module(module_name, __name__), attribute_name)
    globals()[name] = value
    return value
