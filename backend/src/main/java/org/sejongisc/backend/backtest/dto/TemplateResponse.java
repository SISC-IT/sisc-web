package org.sejongisc.backend.backtest.dto;

import lombok.Builder;
import lombok.Getter;
import org.sejongisc.backend.backtest.entity.BacktestRun;
import org.sejongisc.backend.backtest.entity.Template;

import java.util.List;
@Builder
public record TemplateResponse(
        List<Template> templates,
        Template template,
        List<BacktestRun> backtestRuns
) {}