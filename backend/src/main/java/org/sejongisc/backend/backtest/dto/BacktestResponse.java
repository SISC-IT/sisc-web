package org.sejongisc.backend.backtest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.backtest.entity.BacktestRun;
import org.sejongisc.backend.backtest.entity.BacktestRunMetrics;


@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BacktestResponse {
  private BacktestRun backtestRun;
  private BacktestRunMetrics backtestRunMetrics;

}