# AI/modules/collector/__init__.py
from .market_data import update_market_data
from .news_data import collect_news

__all__ = [
    "update_market_data",
    "collect_news",
]