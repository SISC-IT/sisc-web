package org.sejongisc.backend.backtest.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestRunMetrics {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  private BacktestRun backtestRun;

  // precision: 소수점 이하 자리수 포함 총 자릿수, scale: 소수점 이하 자릿수
  @Column(nullable = false, precision = 12, scale = 6)
  private BigDecimal totalReturn;       // 총 수익률

  @Column(nullable = false, precision = 12, scale = 6)
  private BigDecimal maxDrawdown;       // 최대 낙폭, 퍼센티지로 계산됨

  @Column(nullable = false, precision = 12, scale = 6)
  private BigDecimal sharpeRatio;       // 샤프 지수

  @Column(nullable = false, precision = 8, scale = 3)
  private BigDecimal avgHoldDays;       // 평균 보유 기간

  @Column(nullable = false)
  private int tradesCount;              // 총 거래 횟수

  public static BacktestRunMetrics fromDto(BacktestRun backtestRun,
                                           BigDecimal totalReturn,
                                           BigDecimal maxDrawdown,
                                           BigDecimal sharpeRatio,
                                           BigDecimal avgHoldDays,
                                           int tradesCount) {
    return BacktestRunMetrics.builder()
        .backtestRun(backtestRun)
        .totalReturn(totalReturn)
        .maxDrawdown(maxDrawdown)
        .sharpeRatio(sharpeRatio)
        .avgHoldDays(avgHoldDays)
        .tradesCount(tradesCount)
        .build();
  }
}
