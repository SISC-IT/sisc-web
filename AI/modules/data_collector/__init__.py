# AI/modules/data_collector/__init__.py

from .market_data import MarketDataCollector
from .stock_info_collector import StockInfoCollector
from .company_fundamentals_data import FundamentalsDataCollector
from .macro_data import MacroDataCollector
from .crypto_data import CryptoDataCollector
from .index_data import IndexDataCollector
from .event_data import EventDataCollector
# from .news_data import NewsDataCollector  # 뉴스 모듈 구현 시 주석 해제

__all__ = [
    "MarketDataCollector",
    "StockInfoCollector",
    "FundamentalsDataCollector",
    "MacroDataCollector",
    "CryptoDataCollector"
    "IndexDataCollector",
    "EventDataCollector",
    # "NewsDataCollector",  # 뉴스 모듈 구현 시 주석 해제
]