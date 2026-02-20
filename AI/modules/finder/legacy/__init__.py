# AI/modules/finder/legacy/__init__.py
"""
[Finder 패키지]
- 유망 종목 발굴 및 리스트 관리 기능을 제공합니다.
"""

from .evaluator import evaluate_ticker

__all__ = [
    "evaluate_ticker",
]