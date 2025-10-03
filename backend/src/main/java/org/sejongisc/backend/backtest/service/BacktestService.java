package org.sejongisc.backend.backtest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.backtest.dto.BacktestRequest;
import org.sejongisc.backend.backtest.dto.BacktestResponse;
import org.sejongisc.backend.backtest.entity.BacktestRun;
import org.sejongisc.backend.backtest.entity.BacktestRunMetrics;
import org.sejongisc.backend.backtest.repository.BacktestRunMetricsRepository;
import org.sejongisc.backend.backtest.repository.BacktestRunRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.template.entity.Template;
import org.sejongisc.backend.template.repository.TemplateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {
  private final BacktestRunRepository backtestRunRepository;
  private final BacktestRunMetricsRepository backtestRunMetricsRepository;
  private final TemplateRepository templateRepository;

  public BacktestResponse getBacktestStatus(Long backtestRunId, UUID userId) {
    // TODO : 백테스트 상태 조회 로직 구현 (진행 중, 완료, 실패 등)
    BacktestRun backtestRun = findBacktestRunByIdAndVerifyUser(backtestRunId, userId);
    return BacktestResponse.builder()
        .backtestRun(backtestRun)
        .build();
  }
  @Transactional
  public BacktestResponse getBackTestDetails(Long backtestRunId, UUID userId) {
    BacktestRun backtestRun = findBacktestRunByIdAndVerifyUser(backtestRunId, userId);
    BacktestRunMetrics backtestRunMetrics = backtestRunMetricsRepository.findByBacktestRunId(backtestRunId)
        .orElseThrow(() -> new CustomException(ErrorCode.BACKTEST_METRICS_NOT_FOUND));

    return BacktestResponse.builder()
        .backtestRun(backtestRun)
        .backtestRunMetrics(backtestRunMetrics)
        .build();
  }

  @Transactional
  public void deleteBacktest(Long backtestRunId, UUID userId) {
    BacktestRun backtestRun = findBacktestRunByIdAndVerifyUser(backtestRunId, userId);
    backtestRunMetricsRepository.deleteByBacktestRunId(backtestRunId);
    backtestRunRepository.delete(backtestRun);
  }

  @Transactional
  public void addBacktestTemplate(BacktestRequest request) {
    Template template = findTemplateByIdAndVerifyUser(request.getTemplateId(), request.getUserId());
    BacktestRun backtestRun = findBacktestRunByIdAndVerifyUser(request.getBacktestRunId(), request.getUserId());
    backtestRun.updateTemplate(template);
  }

  public BacktestResponse runBacktest(BacktestRequest request) {
    // TODO : 백테스트 실행 로직 구현 (비동기 처리)
    return null;
  }

  @Transactional
  public void deleteBacktestFromTemplate(BacktestRequest request, UUID templateId) {
    findTemplateByIdAndVerifyUser(templateId, request.getUserId());
    List<BacktestRun> backtestRuns = backtestRunRepository.findAllById(request.getBacktestRunIds());

    if (backtestRuns.size() != request.getBacktestRunIds().size()) {
      throw new CustomException(ErrorCode.BACKTEST_NOT_FOUND);
    }

    // 템플릿 매칭, 권한 검증
    for (BacktestRun run : backtestRuns) {
      if (run.getTemplate() == null || !run.getTemplate().getTemplateId().equals(templateId)) {
        throw new CustomException(ErrorCode.BACKTEST_TEMPLATE_MISMATCH);
      }
      if (!run.getUser().getUserId().equals(request.getUserId())) {
        throw new CustomException(ErrorCode.BACKTEST_OWNER_MISMATCH);
      }
    }

    // 벌크 삭제
    backtestRunMetricsRepository.deleteByBacktestRunIdIn(request.getBacktestRunIds());
    backtestRunRepository.deleteAllInBatch(backtestRuns);
  }

  private Template findTemplateByIdAndVerifyUser(UUID templateId, UUID userId) {
    Template template = templateRepository.findById(templateId)
        .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND));

    if (!template.getUser().getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.TEMPLATE_OWNER_MISMATCH);
    }
    return template;
  }

  private BacktestRun findBacktestRunByIdAndVerifyUser(Long backtestRunId, UUID userId) {
    BacktestRun backtestRun = backtestRunRepository.findById(backtestRunId)
        .orElseThrow(() -> new CustomException(ErrorCode.BACKTEST_NOT_FOUND));

    if (!backtestRun.getUser().getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.BACKTEST_OWNER_MISMATCH);
    }
    return backtestRun;
  }
}
