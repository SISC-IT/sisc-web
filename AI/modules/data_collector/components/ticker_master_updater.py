"""
호환성 유지용 래퍼입니다.
- 기존 ticker_master_updater import/실행 경로를 유지
- 실제 구현은 ticker_updater 로 이동
"""

from AI.modules.data_collector.components.ticker_updater import TickerUpdater, main

TickerMasterUpdater = TickerUpdater


if __name__ == "__main__":
    main()
