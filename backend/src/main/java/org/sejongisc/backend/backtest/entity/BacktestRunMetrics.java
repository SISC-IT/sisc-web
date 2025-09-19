package org.sejongisc.backend.backtest.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

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
  private BigDecimal maxDrawdown;       // 최대 낙폭

  @Column(nullable = false, precision = 12, scale = 6)
  private BigDecimal sharpeRatio;       // 샤프 지수

  @Column(nullable = false, precision = 8, scale = 3)
  private BigDecimal avgHoldDays;       // 평균 보유 기간

  @Column(nullable = false)
  private int tradesCount;              // 총 거래 횟수
}
