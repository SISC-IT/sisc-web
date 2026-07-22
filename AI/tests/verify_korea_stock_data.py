from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
import sys
from unittest.mock import MagicMock, patch

import pandas as pd

# 파일을 직접 실행해도 AI 패키지를 찾을 수 있도록 저장소 루트를 모듈 경로에 추가합니다.
PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.append(str(PROJECT_ROOT))

from AI.modules.data_collector.components.korea_stock_data import (
    KoreaStockCollectorConfig,
    KoreaStockDataCollector,
)


class KoreaStockDataCollectorTest(unittest.TestCase):
    """국내주식 수집기의 정규화와 배치 오류 격리 동작을 검증합니다."""

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        temp_root = Path(self.temp_dir.name)
        self.config = KoreaStockCollectorConfig(
            source="fdr",
            markets=("KOSPI",),
            storage="file",
            data_dir=temp_root / "data",
            log_dir=temp_root / "logs",
            sleep_seconds=0,
        )
        self.collector = KoreaStockDataCollector(self.config)

    def tearDown(self) -> None:
        # Windows에서는 FileHandler를 닫아야 임시 로그 디렉터리를 정리할 수 있습니다.
        for handler in self.collector.logger.handlers[:]:
            handler.close()
            self.collector.logger.removeHandler(handler)
        self.temp_dir.cleanup()

    def test_fdr_데이터를_표준_컬럼으로_정규화한다(self) -> None:
        raw = pd.DataFrame(
            {
                "Open": [100, 90, 80],
                "High": [110, 95, 85],
                "Low": [95, 85, 75],
                "Close": [105, 90, 0],
                "Volume": [10, 0, 5],
            },
            index=pd.to_datetime(["2026-07-01", "2026-07-02", "2026-07-03"]),
        )

        result = self.collector._normalize_ohlcv(
            raw,
            ticker="005930",
            market="KOSPI",
            name="삼성전자",
        )

        self.assertEqual(list(result.columns), self.collector.STANDARD_COLUMNS)
        self.assertEqual(len(result), 2)
        self.assertEqual(result["ticker"].tolist(), ["005930", "005930"])
        self.assertEqual(result["market"].tolist(), ["KOSPI", "KOSPI"])
        self.assertEqual(result["name"].tolist(), ["삼성전자", "삼성전자"])
        self.assertEqual(result["trading_value"].tolist(), [1050.0, 0.0])
        self.assertEqual(result["adjusted_close"].tolist(), [105.0, 90.0])
        self.assertEqual(result["volume"].tolist(), [10, 0])

    def test_pykrx_한글_컬럼을_표준_컬럼으로_정규화한다(self) -> None:
        raw = pd.DataFrame(
            {
                "시가": [100],
                "고가": [110],
                "저가": [95],
                "종가": [108],
                "거래량": [20],
                "거래대금": [2160],
            },
            index=pd.to_datetime(["2026-07-01"]),
        )

        result = self.collector._normalize_ohlcv(
            raw,
            ticker="000660",
            market="KOSPI",
            name="SK하이닉스",
        )

        self.assertEqual(result.loc[0, "close"], 108)
        self.assertEqual(result.loc[0, "trading_value"], 2160)
        self.assertEqual(result.loc[0, "amount"], 2160)
        self.assertEqual(result.loc[0, "adjusted_close"], 108)

    def test_개별_종목_실패가_배치_전체를_중단하지_않는다(self) -> None:
        listing = pd.DataFrame(
            [
                {"ticker": "005930", "name": "삼성전자", "market": "KOSPI"},
                {"ticker": "000660", "name": "SK하이닉스", "market": "KOSPI"},
            ]
        )
        success_frame = self.collector._normalize_ohlcv(
            pd.DataFrame(
                {
                    "Open": [100],
                    "High": [110],
                    "Low": [95],
                    "Close": [105],
                    "Volume": [10],
                },
                index=pd.to_datetime(["2026-07-01"]),
            ),
            ticker="005930",
            market="KOSPI",
            name="삼성전자",
        )

        with (
            patch.object(self.collector, "get_ticker_list", return_value=listing),
            patch.object(
                self.collector,
                "fetch_daily_ohlcv",
                side_effect=[success_frame, RuntimeError("테스트용 수집 실패")],
            ),
            patch.object(self.collector, "save_to_file", return_value=self.config.data_dir / "005930.csv"),
        ):
            stats = self.collector.collect_batch(markets=("KOSPI",))

        self.assertEqual(stats, {"success": 1, "empty": 0, "failed": 1, "rows": 1})
        failure_logs = list(self.config.log_dir.glob("failed_tickers_*.csv"))
        self.assertEqual(len(failure_logs), 1)
        failure_frame = pd.read_csv(failure_logs[0], dtype={"ticker": str})
        self.assertEqual(failure_frame.loc[0, "ticker"], "000660")
        self.assertEqual(failure_frame.loc[0, "reason"], "테스트용 수집 실패")

    def test_표준_데이터를_price_data에_upsert한다(self) -> None:
        frame = self.collector._normalize_ohlcv(
            pd.DataFrame(
                {
                    "Open": [100],
                    "High": [110],
                    "Low": [95],
                    "Close": [105],
                    "Volume": [10],
                    "Amount": [1050],
                },
                index=pd.to_datetime(["2026-07-01"]),
            ),
            ticker="005930",
            market="KOSPI",
            name="삼성전자",
        )
        connection = MagicMock()
        cursor = MagicMock()
        connection.cursor.return_value.__enter__.return_value = cursor

        with (
            patch(
                "AI.modules.data_collector.components.korea_stock_data.get_db_conn",
                return_value=connection,
            ),
            patch(
                "AI.modules.data_collector.components.korea_stock_data.execute_values"
            ) as mocked_execute_values,
        ):
            saved_count = self.collector.save_to_db(frame)

        self.assertEqual(saved_count, 1)
        mocked_execute_values.assert_called_once()
        query = mocked_execute_values.call_args.args[1]
        records = mocked_execute_values.call_args.args[2]
        self.assertIn("ON CONFLICT (date, ticker) DO UPDATE", query)
        self.assertEqual(records[0][1], "005930")
        self.assertEqual(records[0][6], 10)
        connection.commit.assert_called_once_with()
        connection.rollback.assert_not_called()
        connection.close.assert_called_once_with()


if __name__ == "__main__":
    unittest.main()
