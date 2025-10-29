package org.sejongisc.backend.backtest.dto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.sejongisc.backend.backtest.entity.BacktestRun;
import org.sejongisc.backend.backtest.entity.BacktestRunMetrics;
import org.sejongisc.backend.backtest.entity.BacktestStatus;
import org.sejongisc.backend.template.entity.Template;
import org.sejongisc.backend.user.entity.User;

import java.time.LocalDate;


@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BacktestResponse {
  private Long id;
  private Template template;
  private String title;
  private BacktestStatus status;
  private String paramsJson;
  private LocalDate startDate;
  private LocalDate endDate;

  private BacktestRunMetrics backtestRunMetrics;
}