package org.sejongisc.backend.backtest.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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
import org.sejongisc.backend.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class BacktestServiceTest {

  @Mock private BacktestRunRepository runRepository;
  @Mock private BacktestRunMetricsRepository metricsRepository;
  @Mock private TemplateRepository templateRepository;

  @InjectMocks private BacktestService backtestService;

  private UUID userId;
  private User user;
  private Template template;
  private BacktestRun run;
  private BacktestRunMetrics metrics;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    userId = UUID.randomUUID();
    user = User.builder().userId(userId).build();
    template = Template.builder().templateId(UUID.randomUUID()).user(user).build();

    run = BacktestRun.builder()
        .id(1L)
        .user(user)
        .template(template)
        .title("Test Run")
        .paramsJson("{\"param\":1}")
        .startDate(LocalDate.now().minusDays(10))
        .endDate(LocalDate.now())
        .startedAt(LocalDateTime.now().minusHours(1))
        .finishedAt(LocalDateTime.now())
        .build();

    metrics = BacktestRunMetrics.builder()
        .id(100L)
        .backtestRun(run)
        .totalReturn(BigDecimal.valueOf(0.15))
        .maxDrawdown(BigDecimal.valueOf(-0.10))
        .sharpeRatio(BigDecimal.valueOf(1.5))
        .avgHoldDays(BigDecimal.valueOf(2.5))
        .tradesCount(10)
        .build();
  }

  // ==============================
  // getBacktestStatus
  // ==============================
  @Test
  @DisplayName("getBacktestStatus - 성공")
  void getBacktestStatus_success() {
    given(runRepository.findById(1L)).willReturn(Optional.of(run));

    BacktestResponse response = backtestService.getBacktestStatus(1L, userId);

//    assertThat(response.getBacktestRun().getTitle()).isEqualTo("Test Run");
  }

  @Test
  @DisplayName("getBacktestStatus - Run 없음 → BACKTEST_NOT_FOUND")
  void getBacktestStatus_notFound() {
    given(runRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> backtestService.getBacktestStatus(1L, userId))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.BACKTEST_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("getBacktestStatus - 유저 불일치 → BACKTEST_OWNER_MISMATCH")
  void getBacktestStatus_ownerMismatch() {
    UUID otherUser = UUID.randomUUID();
    run = BacktestRun.builder().id(1L).user(User.builder().userId(otherUser).build()).build();

    given(runRepository.findById(1L)).willReturn(Optional.of(run));

    assertThatThrownBy(() -> backtestService.getBacktestStatus(1L, userId))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.BACKTEST_OWNER_MISMATCH.getMessage());
  }

  // ==============================
  // getBackTestDetails
  // ==============================
  @Test
  @DisplayName("getBackTestDetails - 성공")
  void getBackTestDetails_success() {
    given(runRepository.findById(1L)).willReturn(Optional.of(run));
    given(metricsRepository.findByBacktestRunId(1L)).willReturn(Optional.of(metrics));

    BacktestResponse response = backtestService.getBackTestDetails(1L, userId);

    assertThat(response.getBacktestRunMetricsResponse().sharpeRatio()).isEqualTo(BigDecimal.valueOf(1.5));
  }

  @Test
  @DisplayName("getBackTestDetails - Run 없음 → BACKTEST_NOT_FOUND")
  void getBackTestDetails_notFound() {
    given(runRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> backtestService.getBackTestDetails(1L, userId))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.BACKTEST_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("getBackTestDetails - 유저 불일치 → BACKTEST_OWNER_MISMATCH")
  void getBackTestDetails_ownerMismatch() {
    run = BacktestRun.builder().id(1L).user(User.builder().userId(UUID.randomUUID()).build()).build();
    given(runRepository.findById(1L)).willReturn(Optional.of(run));

    assertThatThrownBy(() -> backtestService.getBackTestDetails(1L, userId))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.BACKTEST_OWNER_MISMATCH.getMessage());
  }

  @Test
  @DisplayName("getBackTestDetails - Metrics 없음 → BACKTEST_METRICS_NOT_FOUND")
  void getBackTestDetails_metricsNotFound() {
    given(runRepository.findById(1L)).willReturn(Optional.of(run));
    given(metricsRepository.findByBacktestRunId(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> backtestService.getBackTestDetails(1L, userId))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.BACKTEST_METRICS_NOT_FOUND.getMessage());
  }

  // ==============================
  // deleteBacktest
  // ==============================
  @Test
  @DisplayName("deleteBacktest - 성공")
  void deleteBacktest_success() {
    given(runRepository.findById(1L)).willReturn(Optional.of(run));

    backtestService.deleteBacktest(1L, userId);

    verify(metricsRepository, times(1)).deleteByBacktestRunId(1L);
    verify(runRepository, times(1)).delete(run);
  }

  @Test
  @DisplayName("deleteBacktest - Run 없음 → BACKTEST_NOT_FOUND")
  void deleteBacktest_notFound() {
    given(runRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> backtestService.deleteBacktest(1L, userId))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.BACKTEST_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("deleteBacktest - 유저 불일치 → BACKTEST_OWNER_MISMATCH")
  void deleteBacktest_ownerMismatch() {
    run = BacktestRun.builder().id(1L).user(User.builder().userId(UUID.randomUUID()).build()).build();
    given(runRepository.findById(1L)).willReturn(Optional.of(run));

    assertThatThrownBy(() -> backtestService.deleteBacktest(1L, userId))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.BACKTEST_OWNER_MISMATCH.getMessage());
  }

  // ==============================
  // addBacktestTemplate
  // ==============================
  @Test
  @DisplayName("addBacktestTemplate - 성공")
  void addBacktestTemplate_success() {
    BacktestRequest request = new BacktestRequest();
    request.setUserId(userId);
    request.setBacktestRunId(1L);
    request.setTemplateId(template.getTemplateId());

    given(templateRepository.findById(template.getTemplateId())).willReturn(Optional.of(template));
    given(runRepository.findById(1L)).willReturn(Optional.of(run));

    backtestService.addBacktestTemplate(request);

    verify(runRepository, times(1)).findById(1L);
  }

  @Test
  @DisplayName("addBacktestTemplate - Template 없음 → TEMPLATE_NOT_FOUND")
  void addBacktestTemplate_templateNotFound() {
    BacktestRequest request = new BacktestRequest();
    request.setUserId(userId);
    request.setBacktestRunId(1L);
    request.setTemplateId(UUID.randomUUID());

    given(templateRepository.findById(any())).willReturn(Optional.empty());

    assertThatThrownBy(() -> backtestService.addBacktestTemplate(request))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.TEMPLATE_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("addBacktestTemplate - Template 유저 불일치 → TEMPLATE_OWNER_MISMATCH")
  void addBacktestTemplate_templateOwnerMismatch() {
    Template otherTemplate = Template.builder()
        .templateId(UUID.randomUUID())
        .user(User.builder().userId(UUID.randomUUID()).build())
        .build();

    BacktestRequest request = new BacktestRequest();
    request.setUserId(userId);
    request.setBacktestRunId(1L);
    request.setTemplateId(otherTemplate.getTemplateId());

    given(templateRepository.findById(otherTemplate.getTemplateId())).willReturn(Optional.of(otherTemplate));

    assertThatThrownBy(() -> backtestService.addBacktestTemplate(request))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.TEMPLATE_OWNER_MISMATCH.getMessage());
  }

  @Test
  @DisplayName("addBacktestTemplate - Run 없음 → BACKTEST_NOT_FOUND")
  void addBacktestTemplate_runNotFound() {
    BacktestRequest request = new BacktestRequest();
    request.setUserId(userId);
    request.setBacktestRunId(1L);
    request.setTemplateId(template.getTemplateId());

    given(templateRepository.findById(template.getTemplateId())).willReturn(Optional.of(template));
    given(runRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> backtestService.addBacktestTemplate(request))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.BACKTEST_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("addBacktestTemplate - Run 유저 불일치 → BACKTEST_OWNER_MISMATCH")
  void addBacktestTemplate_runOwnerMismatch() {
    BacktestRequest request = new BacktestRequest();
    request.setUserId(userId);
    request.setBacktestRunId(1L);
    request.setTemplateId(template.getTemplateId());

    run = BacktestRun.builder().id(1L).user(User.builder().userId(UUID.randomUUID()).build()).build();

    given(templateRepository.findById(template.getTemplateId())).willReturn(Optional.of(template));
    given(runRepository.findById(1L)).willReturn(Optional.of(run));

    assertThatThrownBy(() -> backtestService.addBacktestTemplate(request))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.BACKTEST_OWNER_MISMATCH.getMessage());
  }

  // ==============================
  // deleteBacktestFromTemplate
  // ==============================
  @Test
  @DisplayName("deleteBacktestFromTemplate - 성공")
  void deleteBacktestFromTemplate_success() {
    BacktestRequest request = new BacktestRequest();
    request.setUserId(userId);
    request.setBacktestRunIds(List.of(1L));

    given(templateRepository.findById(template.getTemplateId())).willReturn(Optional.of(template));
    given(runRepository.findAllById(List.of(1L))).willReturn(List.of(run));

    backtestService.deleteBacktestFromTemplate(request, template.getTemplateId());

    verify(metricsRepository, times(1)).deleteByBacktestRunIdIn(List.of(1L));
    verify(runRepository, times(1)).deleteAllInBatch(List.of(run));
  }

  @Test
  @DisplayName("deleteBacktestFromTemplate - Template 없음 → TEMPLATE_NOT_FOUND")
  void deleteBacktestFromTemplate_templateNotFound() {
    BacktestRequest request = new BacktestRequest();
    request.setUserId(userId);
    request.setBacktestRunIds(List.of(1L));

    given(templateRepository.findById(any())).willReturn(Optional.empty());

    assertThatThrownBy(() -> backtestService.deleteBacktestFromTemplate(request, UUID.randomUUID()))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.TEMPLATE_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("deleteBacktestFromTemplate - Template 유저 불일치 → TEMPLATE_OWNER_MISMATCH")
  void deleteBacktestFromTemplate_templateOwnerMismatch() {
    Template otherTemplate = Template.builder()
        .templateId(UUID.randomUUID())
        .user(User.builder().userId(UUID.randomUUID()).build())
        .build();

    BacktestRequest request = new BacktestRequest();
    request.setUserId(userId);
    request.setBacktestRunIds(List.of(1L));

    given(templateRepository.findById(otherTemplate.getTemplateId())).willReturn(Optional.of(otherTemplate));

    assertThatThrownBy(() -> backtestService.deleteBacktestFromTemplate(request, otherTemplate.getTemplateId()))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.TEMPLATE_OWNER_MISMATCH.getMessage());
  }

  @Test
  @DisplayName("deleteBacktestFromTemplate - Run 일부 없음 → BACKTEST_NOT_FOUND")
  void deleteBacktestFromTemplate_runNotFound() {
    BacktestRequest request = new BacktestRequest();
    request.setUserId(userId);
    request.setBacktestRunIds(List.of(1L, 2L));

    given(templateRepository.findById(template.getTemplateId())).willReturn(Optional.of(template));
    given(runRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(run)); // 2L 없음

    assertThatThrownBy(() -> backtestService.deleteBacktestFromTemplate(request, template.getTemplateId()))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.BACKTEST_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("deleteBacktestFromTemplate - Run Template 불일치 → BACKTEST_TEMPLATE_MISMATCH")
  void deleteBacktestFromTemplate_templateMismatch() {
    BacktestRequest request = new BacktestRequest();
    request.setUserId(userId);
    request.setBacktestRunIds(List.of(1L));

    BacktestRun otherRun = BacktestRun.builder()
        .id(1L)
        .user(user)
        .template(Template.builder().templateId(UUID.randomUUID()).user(user).build())
        .build();

    given(templateRepository.findById(template.getTemplateId())).willReturn(Optional.of(template));
    given(runRepository.findAllById(List.of(1L))).willReturn(List.of(otherRun));

    assertThatThrownBy(() -> backtestService.deleteBacktestFromTemplate(request, template.getTemplateId()))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.BACKTEST_TEMPLATE_MISMATCH.getMessage());
  }

  @Test
  @DisplayName("deleteBacktestFromTemplate - Run 유저 불일치 → BACKTEST_OWNER_MISMATCH")
  void deleteBacktestFromTemplate_ownerMismatch() {
    BacktestRequest request = new BacktestRequest();
    request.setUserId(userId);
    request.setBacktestRunIds(List.of(1L));

    BacktestRun otherRun = BacktestRun.builder()
        .id(1L)
        .user(User.builder().userId(UUID.randomUUID()).build())
        .template(template)
        .build();

    given(templateRepository.findById(template.getTemplateId())).willReturn(Optional.of(template));
    given(runRepository.findAllById(List.of(1L))).willReturn(List.of(otherRun));

    assertThatThrownBy(() -> backtestService.deleteBacktestFromTemplate(request, template.getTemplateId()))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.BACKTEST_OWNER_MISMATCH.getMessage());
  }
}