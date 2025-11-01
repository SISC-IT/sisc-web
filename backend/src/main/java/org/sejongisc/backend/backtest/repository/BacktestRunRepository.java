package org.sejongisc.backend.backtest.repository;

import org.sejongisc.backend.backtest.entity.BacktestRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BacktestRunRepository extends JpaRepository<BacktestRun, Long> {
  @Query("SELECT br FROM BacktestRun br " +
         "JOIN FETCH br.template t " +
         "WHERE t.templateId = :templateTemplateId " +
         "ORDER BY br.startedAt DESC")
  List<BacktestRun> findByTemplate_TemplateIdWithTemplate(UUID templateTemplateId);
}
