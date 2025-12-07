package org.sejongisc.backend.backtest.repository;

import org.sejongisc.backend.backtest.entity.BacktestRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BacktestRunRepository extends JpaRepository<BacktestRun, Long> {
  @Query("SELECT br FROM BacktestRun br " +
         "LEFT JOIN FETCH br.template t " +
         "JOIN FETCH br.user u " +
         "WHERE t.templateId = :templateTemplateId " +
         "ORDER BY br.startedAt DESC")
  List<BacktestRun> findByTemplate_TemplateIdWithTemplate(@Param("templateTemplateId") UUID templateTemplateId);

  @Query("SELECT br FROM BacktestRun br " +
         "LEFT JOIN FETCH br.template t " +     // template은 없을 수 있기에 left join
         "JOIN FETCH br.user u " +
         "WHERE br.id = :backtestRunId ")
  Optional<BacktestRun> findByIdWithMember(@Param("backtestRunId") Long backtestRunId);
}
