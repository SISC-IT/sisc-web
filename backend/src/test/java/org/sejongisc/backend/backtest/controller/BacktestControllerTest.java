package org.sejongisc.backend.backtest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.backtest.dto.BacktestRequest;
import org.sejongisc.backend.backtest.dto.BacktestResponse;
import org.sejongisc.backend.backtest.entity.BacktestRun;
import org.sejongisc.backend.backtest.entity.BacktestRunMetrics;
import org.sejongisc.backend.backtest.service.BacktestService;
import org.sejongisc.backend.common.auth.config.SecurityConfig;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BacktestController.class)
@Import({SecurityConfig.class, BacktestControllerTest.DisableJpaAuditingConfig.class})
@AutoConfigureMockMvc
class BacktestControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private BacktestService backtestService;
  @MockBean JpaMetamodelMappingContext jpaMetamodelMappingContext;
  @MockBean AuditorAware<String> auditorAware;

  private UserDetails 인증_사용자(UUID userId) {
    User u = User.builder()
        .userId(userId)
        .name("tester")
        .email("test@example.com")
        .role(Role.TEAM_MEMBER)
        .point(0)
        .build();
    return new CustomUserDetails(u);
  }

  // ===== 상태 조회 =====
  @Test
  @DisplayName("[GET] /api/backtest/runs/{id}/status : 인증 O → 200 & 상태 반환")
  void 상태조회_인증되어있으면_200() throws Exception {
    UUID uid = UUID.randomUUID();
    UserDetails principal = 인증_사용자(uid);

    BacktestRun run = new BacktestRun();
    BacktestResponse resp = BacktestResponse.builder().backtestRun(run).build();

    when(backtestService.getBacktestStatus(1L, uid)).thenReturn(resp);

    mockMvc.perform(get("/api/backtest/runs/{id}/status", 1L).with(user(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.backtestRun").exists());
  }

  @Test
  @DisplayName("[GET] /api/backtest/runs/{id}/status : 인증 X → 403")
  void 상태조회_미인증이면_403() throws Exception {
    mockMvc.perform(get("/api/backtest/runs/{id}/status", 1L))
        .andExpect(status().isForbidden());
  }

  // ===== 상세 조회 =====
  @Test
  @DisplayName("[GET] /api/backtest/runs/{id} : 인증 O → 200 & 상세 반환")
  void 상세조회_인증되어있으면_200() throws Exception {
    UUID uid = UUID.randomUUID();
    UserDetails principal = 인증_사용자(uid);

    BacktestRun run = new BacktestRun();
    BacktestRunMetrics metrics = new BacktestRunMetrics();
    BacktestResponse resp = BacktestResponse.builder()
        .backtestRun(run)
        .backtestRunMetrics(metrics)
        .build();

    when(backtestService.getBackTestDetails(1L, uid)).thenReturn(resp);

    mockMvc.perform(get("/api/backtest/runs/{id}", 1L).with(user(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.backtestRun").exists())
        .andExpect(jsonPath("$.backtestRunMetrics").exists());
  }

  // ===== 실행 =====
  @Test
  @DisplayName("[POST] /api/backtest/runs : 인증 O → 200 & 실행 시작")
  void 실행_인증되어있으면_200() throws Exception {
    UUID uid = UUID.randomUUID();
    UserDetails principal = 인증_사용자(uid);

    BacktestRequest req = new BacktestRequest();
    req.setUserId(uid);

    BacktestResponse resp = BacktestResponse.builder()
        .backtestRun(new BacktestRun())
        .build();

    when(backtestService.runBacktest(any(BacktestRequest.class))).thenReturn(resp);

    mockMvc.perform(post("/api/backtest/runs")
            .with(user(principal))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.backtestRun").exists());
  }

  // ===== 삭제 =====
  @Test
  @DisplayName("[DELETE] /api/backtest/runs/{id} : 인증 O → 204")
  void 삭제_인증되어있으면_204() throws Exception {
    UUID uid = UUID.randomUUID();
    UserDetails principal = 인증_사용자(uid);

    doNothing().when(backtestService).deleteBacktest(1L, uid);

    mockMvc.perform(delete("/api/backtest/runs/{id}", 1L).with(user(principal)))
        .andExpect(status().isNoContent());
  }

  // ===== 템플릿에 저장 =====
  @Test
  @DisplayName("[PATCH] /api/backtest/runs/{tid} : 인증 O → 200")
  void 템플릿저장_인증되어있으면_200() throws Exception {
    UUID uid = UUID.randomUUID();
    UUID tid = UUID.randomUUID();
    UserDetails principal = 인증_사용자(uid);

    BacktestRequest req = new BacktestRequest();
    req.setUserId(uid);
    req.setTemplateId(tid);

    doNothing().when(backtestService).addBacktestTemplate(any(BacktestRequest.class));

    mockMvc.perform(patch("/api/backtest/runs/{tid}", tid)
            .with(user(principal))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk());
  }

  // ===== 템플릿에서 실행 삭제 =====
  @Test
  @DisplayName("[DELETE] /api/backtest/templates/{tid}/runs : 인증 O → 204")
  void 템플릿삭제_인증되어있으면_204() throws Exception {
    UUID uid = UUID.randomUUID();
    UUID tid = UUID.randomUUID();
    UserDetails principal = 인증_사용자(uid);

    BacktestRequest req = new BacktestRequest();
    req.setUserId(uid);

    doNothing().when(backtestService).deleteBacktestFromTemplate(eq(req), eq(tid));

    mockMvc.perform(delete("/api/backtest/templates/{tid}/runs", tid)
            .with(user(principal))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isNoContent());
  }

  @TestConfiguration
  static class DisableJpaAuditingConfig {
    @Bean
    public AuditorAware<String> auditorProvider() {
      return () -> Optional.of("test-user");
    }
  }
}