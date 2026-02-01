"""
[Backtest Execution Package]
- 단일 종목 및 포트폴리오 단위의 백테스트 실행 함수들을 제공합니다.
"""

# 함수 이름이 겹치지 않게 alias(별칭)를 주어 명확히 구분합니다.
from .run_portfolio import run_backtest as run_portfolio_backtest
from .run_backtrader_single import run_single_backtest

__all__ = ['run_portfolio_backtest', 'run_single_backtest']