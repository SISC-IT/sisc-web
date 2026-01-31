package org.sejongisc.backend.backtest.dto;

import lombok.Builder;
import lombok.Getter;
import org.sejongisc.backend.backtest.entity.BacktestRun;
import org.sejongisc.backend.backtest.entity.Template;

import java.util.List;


@Builder
@Getter
public class TemplateResponse {
  private List<Template> templates;
  private Template template;
  private List<BacktestRun> backtestRuns;
}
