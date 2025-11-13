package org.sejongisc.backend.backtest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.backtest.dto.BacktestRequest;
import org.sejongisc.backend.backtest.dto.BacktestResponse;
import org.sejongisc.backend.backtest.entity.BacktestRun;
import org.sejongisc.backend.backtest.entity.BacktestRunMetrics;
import org.sejongisc.backend.backtest.entity.BacktestStatus;
import org.sejongisc.backend.backtest.repository.BacktestRunMetricsRepository;
import org.sejongisc.backend.backtest.repository.BacktestRunRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.template.entity.Template;
import org.sejongisc.backend.template.repository.TemplateRepository;
import org.sejongisc.backend.user.entity.User;
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
  private final BacktestingEngine backtestingEngine;
  private final ObjectMapper objectMapper;
  private final EntityManager em;

  public BacktestResponse getBacktestStatus(Long backtestRunId, UUID userId) {
    // TODO : 백테스트 상태 조회 로직 구현 (진행 중, 완료, 실패 등)
    BacktestRun backtestRun = findBacktestRunByIdAndVerifyUser(backtestRunId, userId);
    return BacktestResponse.builder()
        .id(backtestRun.getId())
        .paramsJson(backtestRun.getParamsJson())
        .title(backtestRun.getTitle())
        .status(backtestRun.getStatus())
        .startDate(backtestRun.getStartDate())
        .endDate(backtestRun.getEndDate())
        .template(backtestRun.getTemplate())
        .build();
  }
  @Transactional
  public BacktestResponse getBackTestDetails(Long backtestRunId, UUID userId) {
    BacktestRunMetrics backtestRunMetrics = backtestRunMetricsRepository.findByBacktestRunId(backtestRunId)
        .orElse(null);
    BacktestRun backtestRun = findBacktestRunByIdAndVerifyUser(backtestRunId, userId);

    return BacktestResponse.builder()
        .id(backtestRun.getId())
        .paramsJson(backtestRun.getParamsJson())
        .title(backtestRun.getTitle())
        .status(backtestRun.getStatus())
        .startDate(backtestRun.getStartDate())
        .endDate(backtestRun.getEndDate())
        .template(backtestRun.getTemplate())
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
    User userRef = em.getReference(User.class, request.getUserId());

    Template templateRef = null;
    if (request.getTemplateId() != null)
      templateRef =  em.getReference(Template.class, request.getTemplateId());

    String paramsJson;
    try {
      paramsJson = objectMapper.writeValueAsString(request.getStrategy());
    } catch (Exception e) {
      log.error("paramsJson 변환 중 오류 발생", e);
      throw new CustomException(ErrorCode.INVALID_BACKTEST_JSON_PARAMS);
    }

    // BacktestRun 엔티티를 "PENDING" 상태로 생성
    BacktestRun backtestRun = BacktestRun.builder()
        .user(userRef)
        .template(templateRef)
        .title(request.getTitle())
        .paramsJson(paramsJson)
        .startDate(request.getStartDate())
        .endDate(request.getEndDate())
        .status(BacktestStatus.PENDING)
        .build();

    BacktestRun savedRun = backtestRunRepository.save(backtestRun);
    log.info("백테스팅 실행 요청이 성공적으로 처리되었습니다. ID: {}", savedRun.getId());

    // 비동기로 백테스팅 실행 시작
    backtestingEngine.execute(savedRun.getId());

    // 사용자에게 실행 중 응답 반환
    return BacktestResponse.builder()
        .id(savedRun.getId())
        .paramsJson(savedRun.getParamsJson())
        .title(savedRun.getTitle())
        .status(savedRun.getStatus())
        .startDate(savedRun.getStartDate())
        .endDate(savedRun.getEndDate())
        .template(templateRef)
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
    BacktestRun backtestRun = backtestRunRepository.findById(backtestRunId)
        .orElseThrow(() -> new CustomException(ErrorCode.BACKTEST_NOT_FOUND));

    if (!backtestRun.getUser().getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.BACKTEST_OWNER_MISMATCH);
    }
    return backtestRun;
  }
}
