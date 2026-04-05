import unittest

import pandas as pd

from AI.backtests.portfolio_evaluation import (
    compute_avg_holding_days,
    compute_risk_metrics,
    evaluate_portfolio,
    prepare_summary_frame,
)


class PortfolioEvaluationTests(unittest.TestCase):
    def _sample_summary(self) -> pd.DataFrame:
        return pd.DataFrame(
            {
                "date": ["2026-01-02", "2026-01-05", "2026-01-06", "2026-01-07"],
                "total_asset": [10000.0, 10200.0, 10100.0, 10300.0],
            }
        )

    def _sample_executions(self) -> pd.DataFrame:
        return pd.DataFrame(
            [
                {
                    "ticker": "AAA",
                    "fill_date": "2026-01-02",
                    "side": "BUY",
                    "qty": 10,
                    "fill_price": 100.0,
                    "value": 1000.0,
                    "commission": 1.0,
                    "pnl_realized": 0.0,
                },
                {
                    "ticker": "BBB",
                    "fill_date": "2026-01-05",
                    "side": "BUY",
                    "qty": 5,
                    "fill_price": 200.0,
                    "value": 1000.0,
                    "commission": 1.0,
                    "pnl_realized": 0.0,
                },
                {
                    "ticker": "AAA",
                    "fill_date": "2026-01-06",
                    "side": "SELL",
                    "qty": 10,
                    "fill_price": 110.0,
                    "value": 1100.0,
                    "commission": 1.0,
                    "pnl_realized": 100.0,
                },
                {
                    "ticker": "BBB",
                    "fill_date": "2026-01-07",
                    "side": "SELL",
                    "qty": 5,
                    "fill_price": 180.0,
                    "value": 900.0,
                    "commission": 1.0,
                    "pnl_realized": -100.0,
                },
            ]
        )

    def test_prepare_summary_frame_derives_returns_and_drawdown(self) -> None:
        summary = prepare_summary_frame(self._sample_summary(), initial_capital=10000.0)
        self.assertEqual(len(summary), 4)
        self.assertAlmostEqual(float(summary["daily_return"].iloc[0]), 0.0, places=12)
        self.assertAlmostEqual(float(summary["daily_return"].iloc[1]), 0.02, places=8)
        self.assertAlmostEqual(float(summary["drawdown"].min()), -0.009803921568627416, places=12)
        self.assertAlmostEqual(float(summary["equity_curve"].iloc[-1]), 1.03, places=12)

    def test_compute_avg_holding_days_fifo(self) -> None:
        holding_days = compute_avg_holding_days(self._sample_executions())
        self.assertIsNotNone(holding_days)
        self.assertAlmostEqual(float(holding_days), 3.3333333333333335, places=12)

    def test_compute_risk_metrics_var_and_cvar(self) -> None:
        returns = pd.Series([0.01, -0.02, -0.05, 0.03, -0.04], dtype="float64")
        metrics = compute_risk_metrics(returns, risk_free_rate=0.0, var_confidence=0.95)
        expected_var = float(returns.quantile(0.05))

        self.assertIsNotNone(metrics["var"])
        self.assertIsNotNone(metrics["cvar"])
        self.assertAlmostEqual(float(metrics["var"]), expected_var, places=12)
        # For return series, CVaR (tail mean) should be <= VaR at same confidence.
        self.assertLessEqual(float(metrics["cvar"]), float(metrics["var"]))

    def test_evaluate_portfolio_core_and_trade_metrics(self) -> None:
        result = evaluate_portfolio(
            summary_df=self._sample_summary(),
            executions_df=self._sample_executions(),
            initial_capital=10000.0,
            risk_free_rate=0.0,
            var_confidence=0.95,
        )
        metrics = result.metrics

        self.assertEqual(metrics["business_days"], 4)
        self.assertAlmostEqual(float(metrics["final_total_asset"]), 10300.0, places=12)
        self.assertAlmostEqual(float(metrics["final_return"]), 0.03, places=12)
        self.assertAlmostEqual(float(metrics["max_drawdown"]), -0.009803921568627416, places=12)

        self.assertEqual(int(metrics["trades_total"]), 4)
        self.assertEqual(int(metrics["buy_count"]), 2)
        self.assertEqual(int(metrics["sell_count"]), 2)
        self.assertAlmostEqual(float(metrics["transaction_cost_total"]), 4.0, places=12)
        self.assertAlmostEqual(float(metrics["realized_win_rate"]), 0.5, places=12)
        self.assertAlmostEqual(float(metrics["profit_factor"]), 1.0, places=12)
        self.assertAlmostEqual(float(metrics["avg_holding_days"]), 3.3333333333333335, places=12)

        expected_turnover = 4000.0 / ((10000.0 + 10200.0 + 10100.0 + 10300.0) / 4.0)
        self.assertAlmostEqual(float(metrics["turnover_ratio"]), expected_turnover, places=12)
        self.assertEqual(len(result.monthly_returns), 1)


if __name__ == "__main__":
    unittest.main()
