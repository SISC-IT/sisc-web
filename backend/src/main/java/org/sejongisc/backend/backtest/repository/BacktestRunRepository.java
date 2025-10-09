package org.sejongisc.backend.backtest.repository;

import org.sejongisc.backend.backtest.entity.BacktestRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestRunRepository extends JpaRepository<BacktestRun, Long> {
}
