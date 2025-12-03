package org.sejongisc.backend.backtest.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.backtest.entity.BacktestRunMetrics;

import java.math.BigDecimal;


@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestRunMetricsResponse {
  private Long id;
  private BigDecimal totalReturn;       // 총 수익률
  private BigDecimal maxDrawdown;       // 최대 낙폭
  private BigDecimal sharpeRatio;       // 샤프 지수
  private BigDecimal avgHoldDays;       // 평균 보유 기간
  private int tradesCount;              // 총 거래 횟수
  private String assetCurveJson;

  public static BacktestRunMetricsResponse fromEntity(BacktestRunMetrics backtestRunMetrics) {
    return BacktestRunMetricsResponse.builder()
        .id(backtestRunMetrics.getId())
        .totalReturn(backtestRunMetrics.getTotalReturn())
        .maxDrawdown(backtestRunMetrics.getMaxDrawdown())
        .sharpeRatio(backtestRunMetrics.getSharpeRatio())
        .avgHoldDays(backtestRunMetrics.getAvgHoldDays())
        .tradesCount(backtestRunMetrics.getTradesCount())
        .assetCurveJson(backtestRunMetrics.getAssetCurveJson())
        .build();
  }
}
