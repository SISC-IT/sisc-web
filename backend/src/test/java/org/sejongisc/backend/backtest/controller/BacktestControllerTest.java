//package org.sejongisc.backend.backtest.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.sejongisc.backend.backtest.dto.BacktestRequest;
//import org.sejongisc.backend.backtest.dto.BacktestResponse;
//import org.sejongisc.backend.backtest.entity.BacktestRun;
//import org.sejongisc.backend.backtest.entity.BacktestRunMetrics;
//import org.sejongisc.backend.backtest.service.BacktestService;
//import org.sejongisc.backend.common.auth.config.SecurityConfig;
//import org.sejongisc.backend.common.auth.jwt.JwtParser;
//import org.sejongisc.backend.common.auth.dto.LoginResponse.CustomUserDetails;
//import org.sejongisc.backend.user.entity.Role;
//import org.sejongisc.backend.user.entity.User;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.context.TestConfiguration;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Import;
//import org.springframework.data.domain.AuditorAware;
//import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
//import org.springframework.http.MediaType;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.doNothing;
//import static org.mockito.Mockito.when;
//import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@WebMvcTest(BacktestController.class)
//@Import({SecurityConfig.class, BacktestControllerTest.DisableJpaAuditingConfig.class})
//@AutoConfigureMockMvc
//class BacktestControllerTest {
//
//  @Autowired private MockMvc mockMvc;
//  @Autowired private ObjectMapper objectMapper;
//
//  @MockBean private BacktestService backtestService;
//  @MockBean JpaMetamodelMappingContext jpaMetamodelMappingContext;
//  @MockBean AuditorAware<String> auditorAware;
//  @MockBean
//  JwtParser jwtParser;
//
//  private final static String TOKEN = "TEST_TOKEN";
//
//  private UsernamePasswordAuthenticationToken 인증토큰(UUID uid) {
//    User domainUser = User.builder()
//        .userId(uid).name("tester").email("test@example.com")
//        .role(Role.TEAM_MEMBER).point(0).build();
//
//    CustomUserDetails customUserDetails = new CustomUserDetails(domainUser);
//
//    // SecurityConfig 에서 hasRole("TEAM_MEMBER") 라면 ROLE_ 접두어 필요
//    return new UsernamePasswordAuthenticationToken(customUserDetails, "", List.of(new SimpleGrantedAuthority("ROLE_TEAM_MEMBER")));
//  }
//
//  // ===== 상태 조회 =====
//  @Test
//  @DisplayName("[GET] /api/backtest/runs/{id}/status : 인증 O → 200 & 상태 반환")
//  void 상태조회_인증되어있으면_200() throws Exception {
//    UUID uid = UUID.randomUUID();
//    BacktestRun run = new BacktestRun();
//    BacktestResponse resp = BacktestResponse.builder().backtestRun(run).build();
//
//    // when
//    when(jwtParser.validationToken(TOKEN)).thenReturn(true);
//    when(jwtParser.getAuthentication(TOKEN)).thenReturn(인증토큰(uid));
//    when(backtestService.getBacktestStatus(1L, uid)).thenReturn(resp);
//
//    mockMvc.perform(get("/api/backtest/runs/{id}/status", 1L)
//            .header("Authorization", "Bearer " + TOKEN))
//        .andExpect(status().isOk())
//        .andExpect(jsonPath("$.backtestRun").exists());
//  }
//
//  @Test
//  @DisplayName("[GET] /api/backtest/runs/{id}/status : 인증 X → 403")
//  void 상태조회_미인증이면_403() throws Exception {
//    mockMvc.perform(get("/api/backtest/runs/{id}/status", 1L))
//        .andExpect(status().isUnauthorized());
//  }
//
//  // ===== 상세 조회 =====
//  @Test
//  @DisplayName("[GET] /api/backtest/runs/{id} : 인증 O → 200 & 상세 반환")
//  void 상세조회_인증되어있으면_200() throws Exception {
//    UUID uid = UUID.randomUUID();
//    BacktestRun run = new BacktestRun();
//    BacktestRunMetrics metrics = new BacktestRunMetrics();
//    BacktestResponse resp = BacktestResponse.builder()
//        .backtestRun(run)
//        .backtestRunMetrics(metrics)
//        .build();
//    //when
//    when(jwtParser.validationToken(TOKEN)).thenReturn(true);
//    when(jwtParser.getAuthentication(TOKEN)).thenReturn(인증토큰(uid));
//    when(backtestService.getBackTestDetails(1L, uid)).thenReturn(resp);
//
//    mockMvc.perform(get("/api/backtest/runs/{id}", 1L)
//            .header("Authorization", "Bearer " + TOKEN))
//        .andExpect(status().isOk())
//        .andExpect(jsonPath("$.backtestRun").exists())
//        .andExpect(jsonPath("$.backtestRunMetrics").exists());
//  }
//
//  // ===== 실행 =====
//  @Test
//  @DisplayName("[POST] /api/backtest/runs : 인증 O → 200 & 실행 시작")
//  void 실행_인증되어있으면_200() throws Exception {
//    UUID uid = UUID.randomUUID();
//    BacktestRequest req = new BacktestRequest();
//    req.setUserId(uid);
//    BacktestResponse resp = BacktestResponse.builder()
//        .backtestRun(new BacktestRun())
//        .build();
//
//    // when
//    when(jwtParser.validationToken(TOKEN)).thenReturn(true);
//    when(jwtParser.getAuthentication(TOKEN)).thenReturn(인증토큰(uid));
//    when(backtestService.runBacktest(any(BacktestRequest.class))).thenReturn(resp);
//
//    mockMvc.perform(post("/api/backtest/runs")
//            .header("Authorization", "Bearer " + TOKEN)
//            .contentType(MediaType.APPLICATION_JSON)
//            .content(objectMapper.writeValueAsString(req)))
//        .andExpect(status().isOk())
//        .andExpect(jsonPath("$.backtestRun").exists());
//  }
//
//  // ===== 삭제 =====
//  @Test
//  @DisplayName("[DELETE] /api/backtest/runs/{id} : 인증 O → 204")
//  void 삭제_인증되어있으면_204() throws Exception {
//    UUID uid = UUID.randomUUID();
//    doNothing().when(backtestService).deleteBacktest(1L, uid);
//    when(jwtParser.validationToken(TOKEN)).thenReturn(true);
//    when(jwtParser.getAuthentication(TOKEN)).thenReturn(인증토큰(uid));
//
//    mockMvc.perform(delete("/api/backtest/runs/{id}", 1L)
//            .header("Authorization", "Bearer " + TOKEN))
//        .andExpect(status().isNoContent());
//  }
//
//  // ===== 템플릿에 저장 =====
//  @Test
//  @DisplayName("[PATCH] /api/backtest/runs/{tid} : 인증 O → 200")
//  void 템플릿저장_인증되어있으면_200() throws Exception {
//    UUID uid = UUID.randomUUID();
//    UUID tid = UUID.randomUUID();
//    BacktestRequest req = new BacktestRequest();
//    req.setUserId(uid);
//    req.setTemplateId(tid);
//
//    // when
//    doNothing().when(backtestService).addBacktestTemplate(any(BacktestRequest.class));
//    when(jwtParser.validationToken(TOKEN)).thenReturn(true);
//    when(jwtParser.getAuthentication(TOKEN)).thenReturn(인증토큰(uid));
//
//    mockMvc.perform(patch("/api/backtest/runs/{tid}", tid)
//            .header("Authorization", "Bearer " + TOKEN)
//            .contentType(MediaType.APPLICATION_JSON)
//            .content(objectMapper.writeValueAsString(req)))
//        .andExpect(status().isOk());
//  }
//
//  // ===== 템플릿에서 실행 삭제 =====
//  @Test
//  @DisplayName("[DELETE] /api/backtest/templates/{tid}/runs : 인증 O → 204")
//  void 템플릿삭제_인증되어있으면_204() throws Exception {
//    UUID uid = UUID.randomUUID();
//    UUID tid = UUID.randomUUID();
//    BacktestRequest req = new BacktestRequest();
//    req.setUserId(uid);
//
//    //when
//    doNothing().when(backtestService).deleteBacktestFromTemplate(eq(req), eq(tid));
//    when(jwtParser.validationToken(TOKEN)).thenReturn(true);
//    when(jwtParser.getAuthentication(TOKEN)).thenReturn(인증토큰(uid));
//
//    mockMvc.perform(delete("/api/backtest/templates/{tid}/runs", tid)
//            .header("Authorization", "Bearer " + TOKEN)
//            .contentType(MediaType.APPLICATION_JSON)
//            .content(objectMapper.writeValueAsString(req)))
//        .andExpect(status().isNoContent());
//  }
//
//  @TestConfiguration
//  static class DisableJpaAuditingConfig {
//    @Bean
//    public AuditorAware<String> auditorProvider() {
//      return () -> Optional.of("test-user");
//    }
//  }
//}