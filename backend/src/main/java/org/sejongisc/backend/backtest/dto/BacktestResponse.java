package org.sejongisc.backend.backtest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.backtest.entity.BacktestRun;
import java.util.List;


@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BacktestResponse {
  private BacktestRun backtestRun;
  private BacktestRunMetricsResponse backtestRunMetricsResponse;
  private List<String> availableTickers;
}