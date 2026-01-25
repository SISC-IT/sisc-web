package org.sejongisc.backend.backtest.dto;


import org.sejongisc.backend.backtest.entity.BacktestRunMetrics;

import java.math.BigDecimal;

public record BacktestRunMetricsResponse(
        Long id,
        BigDecimal totalReturn,       // 총 수익률
        BigDecimal maxDrawdown,       // 최대 낙폭
        BigDecimal sharpeRatio,       // 샤프 지수
        BigDecimal avgHoldDays,       // 평균 보유 기간
        int tradesCount,              // 총 거래 횟수
        String assetCurveJson
) {
  public static BacktestRunMetricsResponse fromEntity(BacktestRunMetrics backtestRunMetrics) {
    return new BacktestRunMetricsResponse(
            backtestRunMetrics.getId(),
            backtestRunMetrics.getTotalReturn(),
            backtestRunMetrics.getMaxDrawdown(),
            backtestRunMetrics.getSharpeRatio(),
            backtestRunMetrics.getAvgHoldDays(),
            backtestRunMetrics.getTradesCount(),
            backtestRunMetrics.getAssetCurveJson()
    );
  }
}