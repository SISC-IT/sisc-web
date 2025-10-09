package org.sejongisc.backend.template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.common.auth.config.SecurityConfig;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
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
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "JWT_SECRET=test-secret",
        "SPRING_DATASOURCE_URL=jdbc:h2:mem:testdb",
        "SPRING_DATASOURCE_USERNAME=sa",
        "SPRING_DATASOURCE_PASSWORD=",
        "FIREBASE_CREDENTIAL_PATH=classpath:firebase/test-key.json"
})
class TemplateControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private TemplateService templateService;
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

  // ===== 목록 조회 =====
  @Test
  @DisplayName("[GET] /api/backtest/templates : 인증 O → 200 & 리스트 반환")
  void 목록조회_인증되어있으면_200과_리스트를_반환한다() throws Exception {
    UUID uid = UUID.randomUUID();
    UserDetails principal = 인증_사용자(uid);

    Template t1 = Template.of(User.builder().userId(uid).name("tester").email("test@example.com").build(),
        "t1", "d1", true);
    Template t2 = Template.of(User.builder().userId(uid).name("tester").email("test@example.com").build(),
        "t2", "d2", false);

    TemplateResponse resp = TemplateResponse.builder()
        .templates(List.of(t1, t2))
        .build();

    when(templateService.findAllByUserId(uid)).thenReturn(resp);

    mockMvc.perform(get("/api/backtest/templates").with(user(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.templates[0].title").value("t1"))
        .andExpect(jsonPath("$.templates[1].isPublic").value(false));
  }

  @Test
  @DisplayName("[GET] /api/backtest/templates : 인증 X → 403")
  void 목록조회_미인증이면_403을_반환한다() throws Exception {
    mockMvc.perform(get("/api/backtest/templates"))
        .andExpect(status().isForbidden());
  }

  // ===== 상세 조회 =====
  @Test
  @DisplayName("[GET] /api/backtest/templates/{id} : 인증 O → 200 & 단건 반환")
  void 상세조회_인증되어있으면_200과_단건을_반환한다() throws Exception {
    UUID uid = UUID.randomUUID();
    UUID tid = UUID.randomUUID();
    UserDetails principal = 인증_사용자(uid);

    Template t = Template.of(
        User.builder().userId(uid).name("tester").email("test@example.com").build(),
        "title", "desc", true);

    when(templateService.findById(tid))
        .thenReturn(TemplateResponse.builder().template(t).build());

    mockMvc.perform(get("/api/backtest/templates/{templateId}", tid).with(user(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.template.title").value("title"));
  }

  @Test
  @DisplayName("[GET] /api/backtest/templates/{id} : 인증 X → 403")
  void 상세조회_미인증이면_403을_반환한다() throws Exception {
    mockMvc.perform(get("/api/backtest/templates/{templateId}", UUID.randomUUID()))
        .andExpect(status().isForbidden());
  }

  // ===== 생성 =====
  @Test
  @DisplayName("[POST] /api/backtest/templates : 인증 O → 200 & 생성")
  void 생성_인증되어있으면_200과_생성결과를_반환한다() throws Exception {
    UUID uid = UUID.randomUUID();
    UserDetails principal = 인증_사용자(uid);

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
            .with(user(principal))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.template.title").value("new title"))
        .andExpect(jsonPath("$.template.isPublic").value(true));
  }

  @Test
  @DisplayName("[POST] /api/backtest/templates : 인증 X → 403")
  void 생성_미인증이면_403을_반환한다() throws Exception {
    TemplateRequest req = new TemplateRequest();
    req.setTitle("new title");
    req.setDescription("new desc");
    req.setIsPublic(true);

    mockMvc.perform(post("/api/backtest/templates")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isForbidden());
  }

  // ===== 수정 =====
  @Test
  @DisplayName("[PATCH] /api/backtest/templates/{id} : 인증 O → 200 & 수정")
  void 수정_인증되어있으면_200과_수정결과를_반환한다() throws Exception {
    UUID uid = UUID.randomUUID();
    UUID tid = UUID.randomUUID();
    UserDetails principal = 인증_사용자(uid);

    TemplateRequest req = new TemplateRequest();
    req.setTemplateId(tid);
    req.setTitle("edited");
    req.setDescription("edited desc");
    req.setIsPublic(false);

    Template edited = Template.of(
        User.builder().userId(uid).name("tester").email("test@example.com").build(),
        "edited", "edited desc", false);

    when(templateService.updateTemplate(any(TemplateRequest.class)))
        .thenReturn(TemplateResponse.builder().template(edited).build());

    mockMvc.perform(patch("/api/backtest/templates/{templateId}", tid)
            .with(user(principal))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.template.title").value("edited"))
        .andExpect(jsonPath("$.template.isPublic").value(false));
  }

  @Test
  @DisplayName("[PATCH] /api/backtest/templates/{id} : 인증 X → 403")
  void 수정_미인증이면_403을_반환한다() throws Exception {
    UUID tid = UUID.randomUUID();

    TemplateRequest req = new TemplateRequest();
    req.setTemplateId(tid);
    req.setTitle("edited");
    req.setDescription("edited desc");
    req.setIsPublic(false);

    mockMvc.perform(patch("/api/backtest/templates/{templateId}", tid)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isForbidden());
  }

  // ===== 삭제 =====
  @Test
  @DisplayName("[DELETE] /api/backtest/templates/{id} : 인증 O → 204 & 삭제")
  void 삭제_인증되어있으면_204을_반환한다() throws Exception {
    UUID uid = UUID.randomUUID();
    UUID tid = UUID.randomUUID();
    UserDetails principal = 인증_사용자(uid);

    mockMvc.perform(delete("/api/backtest/templates/{templateId}", tid)
            .with(user(principal)))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("[DELETE] /api/backtest/templates/{id} : 인증 X → 403")
  void 삭제_미인증이면_403을_반환한다() throws Exception {
    mockMvc.perform(delete("/api/backtest/templates/{templateId}", UUID.randomUUID()))
        .andExpect(status().isForbidden());
  }

  @TestConfiguration
  static class DisableJpaAuditingConfig {
    @Bean
    public AuditorAware<String> auditorProvider() {
      return () -> Optional.of("test-user");
    }
  }
}