package org.sejongisc.backend.backtest.repository;

import org.sejongisc.backend.backtest.entity.BacktestRunMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BacktestRunMetricsRepository extends JpaRepository<BacktestRunMetrics, Long> {
  void deleteByBacktestRunId(Long backtestRunId);
  void deleteByBacktestRunIdIn(List<Long> backtestRunIds);
  Optional<BacktestRunMetrics> findByBacktestRunId(Long backtestRunId);
}
