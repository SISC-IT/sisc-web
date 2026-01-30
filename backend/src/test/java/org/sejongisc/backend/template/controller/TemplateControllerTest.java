package org.sejongisc.backend.template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.common.config.SecurityConfig;
import org.sejongisc.backend.common.auth.dto.LoginResponse;
import org.sejongisc.backend.common.auth.jwt.JwtParser;
import org.sejongisc.backend.template.dto.TemplateRequest;
import org.sejongisc.backend.template.dto.TemplateResponse;
import org.sejongisc.backend.template.entity.Template;
import org.sejongisc.backend.template.service.TemplateService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TemplateController.class)
@Import({SecurityConfig.class, TemplateControllerTest.DisableJpaAuditingConfig.class})
@AutoConfigureMockMvc
class TemplateControllerTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private TemplateService templateService;

  @MockBean
  JwtParser jwtParser;

  @MockBean
  JpaMetamodelMappingContext jpaMetamodelMappingContext;
  @MockBean
  AuditorAware<String> auditorAware;

  private final static String TOKEN = "TEST_TOKEN";

  private UsernamePasswordAuthenticationToken 인증토큰(UUID uid) {
    User domainUser = User.builder()
        .userId(uid).name("tester").email("test@example.com")
        .role(Role.TEAM_MEMBER).point(0).build();

    LoginResponse.CustomUserDetails customUserDetails = new LoginResponse.CustomUserDetails(domainUser);

    // SecurityConfig 에서 hasRole("TEAM_MEMBER") 라면 ROLE_ 접두어 필요
    return new UsernamePasswordAuthenticationToken(customUserDetails, "", List.of(new SimpleGrantedAuthority("ROLE_TEAM_MEMBER")));
  }


  // ===== 목록 조회 =====
  @Test
  @DisplayName("[GET] /api/backtest/templates : 인증 O → 200 & 리스트 반환")
  void 목록조회_인증되어있으면_200과_리스트를_반환한다() throws Exception {
    UUID uid = UUID.randomUUID();
    Template t1 = Template.of(User.builder().userId(uid).name("tester").email("test@example.com").build(),
            "t1", "d1", true);
    Template t2 = Template.of(User.builder().userId(uid).name("tester").email("test@example.com").build(),
            "t2", "d2", false);
    TemplateResponse resp = TemplateResponse.builder()
            .templates(List.of(t1, t2))
            .build();

    // when
    when(jwtParser.validationToken(TOKEN)).thenReturn(true);
    when(jwtParser.getAuthentication(TOKEN)).thenReturn(인증토큰(uid));
    when(templateService.findAllByUserId(uid)).thenReturn(resp);

    mockMvc.perform(get("/api/backtest/templates")
            .header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.templates[0].title").value("t1"))
            .andExpect(jsonPath("$.templates[1].isPublic").value(false));
  }

  @Test
  @DisplayName("[GET] /api/backtest/templates : 인증 X → 401")
  void 목록조회_미인증이면_401을_반환한다() throws Exception {
    mockMvc.perform(get("/api/backtest/templates"))
            .andExpect(status().isUnauthorized());
  }

  // ===== 상세 조회 =====
  @Test
  @DisplayName("[GET] /api/backtest/templates/{id} : 인증 O → 200 & 단건 반환")
  void 상세조회_인증되어있으면_200과_단건을_반환한다() throws Exception {
    UUID uid = UUID.randomUUID();
    UUID tid = UUID.randomUUID();
    Template t = Template.of(
        User.builder().userId(uid).name("tester").email("test@example.com").build(),
        "title", "desc", true);
    // when
    when(jwtParser.validationToken(TOKEN)).thenReturn(true);
    when(jwtParser.getAuthentication(TOKEN)).thenReturn(인증토큰(uid));
    when(templateService.findById(tid))
            .thenReturn(TemplateResponse.builder().template(t).build());

    mockMvc.perform(get("/api/backtest/templates/{templateId}", tid)
            .header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.template.title").value("title"));
  }

  @Test
  @DisplayName("[GET] /api/backtest/templates/{id} : 인증 X → 401")
  void 상세조회_미인증이면_401을_반환한다() throws Exception {
    mockMvc.perform(get("/api/backtest/templates/{templateId}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
  }

  // ===== 생성 =====
  @Test
  @DisplayName("[POST] /api/backtest/templates : 인증 O → 200 & 생성")
  void 생성_인증되어있으면_200과_생성결과를_반환한다() throws Exception {
    UUID uid = UUID.randomUUID();

    when(jwtParser.validationToken(TOKEN)).thenReturn(true);
    when(jwtParser.getAuthentication(TOKEN)).thenReturn(인증토큰(uid));

    TemplateRequest req = new TemplateRequest();
    req.setTitle("new title");
    req.setDescription("new desc");
    req.setIsPublic(true);

    Template created = Template.of(
            User.builder().userId(uid).name("tester").email("test@example.com").build(),
            "new title", "new desc", true);

    when(templateService.createTemplate(any(TemplateRequest.class)))
            .thenReturn(TemplateResponse.builder().template(created).build());

    mockMvc.perform(post("/api/backtest/templates")
                    .header("Authorization", "Bearer " + TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.template.title").value("new title"))
            .andExpect(jsonPath("$.template.isPublic").value(true));
  }

  @Test
  @DisplayName("[POST] /api/backtest/templates : 인증 X → 401")
  void 생성_미인증이면_401을_반환한다() throws Exception {
    TemplateRequest req = new TemplateRequest();
    req.setTitle("new title");
    req.setDescription("new desc");
    req.setIsPublic(true);

    mockMvc.perform(post("/api/backtest/templates")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnauthorized());
  }

  // ===== 수정 =====
  @Test
  @DisplayName("[PATCH] /api/backtest/templates/{id} : 인증 O → 200 & 수정")
  void 수정_인증되어있으면_200과_수정결과를_반환한다() throws Exception {
    UUID uid = UUID.randomUUID();
    UUID tid = UUID.randomUUID();
    TemplateRequest req = new TemplateRequest();
    req.setTemplateId(tid);
    req.setTitle("edited");
    req.setDescription("edited desc");
    req.setIsPublic(false);

    Template edited = Template.of(
            User.builder().userId(uid).name("tester").email("test@example.com").build(),
            "edited", "edited desc", false);

    // when
    when(jwtParser.validationToken(TOKEN)).thenReturn(true);
    when(jwtParser.getAuthentication(TOKEN)).thenReturn(인증토큰(uid));
    when(templateService.updateTemplate(any(TemplateRequest.class)))
            .thenReturn(TemplateResponse.builder().template(edited).build());

    // then
    mockMvc.perform(patch("/api/backtest/templates/{templateId}", tid)
                    .header("Authorization", "Bearer " + TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.template.title").value("edited"))
            .andExpect(jsonPath("$.template.isPublic").value(false));
  }

  @Test
  @DisplayName("[PATCH] /api/backtest/templates/{id} : 인증 X → 401")
  void 수정_미인증이면_401을_반환한다() throws Exception {
    UUID tid = UUID.randomUUID();

    TemplateRequest req = new TemplateRequest();
    req.setTemplateId(tid);
    req.setTitle("edited");
    req.setDescription("edited desc");
    req.setIsPublic(false);

    mockMvc.perform(patch("/api/backtest/templates/{templateId}", tid)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnauthorized());
  }

  // ===== 삭제 =====
  @Test
  @DisplayName("[DELETE] /api/backtest/templates/{id} : 인증 O → 204 & 삭제")
  void 삭제_인증되어있으면_204을_반환한다() throws Exception {
    UUID uid = UUID.randomUUID();
    UUID tid = UUID.randomUUID();

    // when
    when(jwtParser.validationToken(TOKEN)).thenReturn(true);
    when(jwtParser.getAuthentication(TOKEN)).thenReturn(인증토큰(uid));

    // then
    mockMvc.perform(delete("/api/backtest/templates/{templateId}", tid)
            .header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("[DELETE] /api/backtest/templates/{id} : 인증 X → 401")
  void 삭제_미인증이면_401을_반환한다() throws Exception {
    mockMvc.perform(delete("/api/backtest/templates/{templateId}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
  }

  @TestConfiguration
  static class DisableJpaAuditingConfig {
    @Bean
    public AuditorAware<String> auditorProvider() {
      return () -> Optional.of("test-user");
    }
  }
}