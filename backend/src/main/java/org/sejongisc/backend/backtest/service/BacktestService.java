package org.sejongisc.backend.backtest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.backtest.dto.BacktestRequest;
import org.sejongisc.backend.backtest.dto.BacktestResponse;
import org.sejongisc.backend.backtest.dto.BacktestRunMetricsResponse;
import org.sejongisc.backend.backtest.entity.BacktestRun;
import org.sejongisc.backend.backtest.entity.BacktestRunMetrics;
import org.sejongisc.backend.backtest.entity.BacktestStatus;
import org.sejongisc.backend.backtest.repository.BacktestRunMetricsRepository;
import org.sejongisc.backend.backtest.repository.BacktestRunRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.stock.repository.PriceDataRepository;
import org.sejongisc.backend.template.entity.Template;
import org.sejongisc.backend.template.repository.TemplateRepository;
import org.sejongisc.backend.user.repository.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {
  private final BacktestRunRepository backtestRunRepository;
  private final BacktestRunMetricsRepository backtestRunMetricsRepository;
  private final TemplateRepository templateRepository;
  private final BacktestingEngine backtestingEngine;
  private final ObjectMapper objectMapper;
  private final UserRepository userRepository;
  private final PriceDataRepository priceDataRepository;

  // 백테스트용 주식 정보 조회
  @Transactional
  public BacktestResponse getBacktestStockInfo() {
    return BacktestResponse.builder()
        .availableTickers(priceDataRepository.findDistinctTickers())
        .build();
  }

  @Transactional
  public BacktestResponse getBacktestStatus(Long backtestRunId, UUID userId) {
    log.info("백테스팅 실행 상태 조회를 시작합니다.");
    BacktestRun backtestRun = findBacktestRunByIdAndVerifyUser(backtestRunId, userId);
    return BacktestResponse.builder()
        .backtestRun(backtestRun)
        .build();
  }
  @Transactional
  public BacktestResponse getBackTestDetails(Long backtestRunId, UUID userId) {
    BacktestRun backtestRun = findBacktestRunByIdAndVerifyUser(backtestRunId, userId);

    if (backtestRun.getStatus() != BacktestStatus.COMPLETED) {
      return BacktestResponse.builder()
          .backtestRun(backtestRun)
          .build();
    }

    BacktestRunMetrics backtestRunMetrics = backtestRunMetricsRepository.findByBacktestRunId(backtestRunId)
        .orElseThrow(() -> new CustomException(ErrorCode.BACKTEST_METRICS_NOT_FOUND));

    return BacktestResponse.builder()
        .backtestRun(backtestRun)
        .backtestRunMetricsResponse(BacktestRunMetricsResponse.fromEntity(backtestRunMetrics))
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
    User user = userRepository.findById(request.getUserId())
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    Template template = null;
    if (request.getTemplateId() != null)
      template = findTemplateByIdAndVerifyUser(request.getTemplateId(), request.getUserId());

    String paramsJson;
    try {
      paramsJson = objectMapper.writeValueAsString(request.getStrategy());
    } catch (Exception e) {
      log.error("paramsJson 변환 중 오류 발생", e);
      throw new CustomException(ErrorCode.INVALID_BACKTEST_JSON_PARAMS);
    }

    // BacktestRun 엔티티를 "PENDING" 상태로 생성
    BacktestRun backtestRun = BacktestRun.builder()
        .user(user)
        .template(template)
        .title(request.getTitle())
        .paramsJson(paramsJson)
        .startDate(request.getStartDate())
        .endDate(request.getEndDate())
        .status(BacktestStatus.PENDING)
        .build();

    BacktestRun savedRun = backtestRunRepository.save(backtestRun);
    log.info("백테스팅 실행 요청이 성공적으로 처리되었습니다. ID: {}", savedRun.getId());

    // 비동기로 백테스팅 실행 시작
    backtestingEngine.execute(savedRun);

    // 사용자에게 실행 중 응답 반환
    return BacktestResponse.builder()
        .backtestRun(savedRun)
        .build();
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
    BacktestRun backtestRun = backtestRunRepository.findByIdWithMember(backtestRunId)
        .orElseThrow(() -> new CustomException(ErrorCode.BACKTEST_NOT_FOUND));

    if (!backtestRun.getUser().getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.BACKTEST_OWNER_MISMATCH);
    }
    return backtestRun;
  }
}
