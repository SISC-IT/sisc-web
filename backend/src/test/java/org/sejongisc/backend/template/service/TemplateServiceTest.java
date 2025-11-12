package org.sejongisc.backend.template.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.template.dto.TemplateRequest;
import org.sejongisc.backend.template.dto.TemplateResponse;
import org.sejongisc.backend.template.entity.Template;
import org.sejongisc.backend.template.repository.TemplateRepository;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.dao.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TemplateServiceTest {

  @Mock
  private TemplateRepository templateRepository;

  @Mock
  private EntityManager em;

  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private TemplateService templateService;

  private UUID userId;
  private UUID templateId;
  private User user;
  private Template template;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    userId = UUID.randomUUID();
    templateId = UUID.randomUUID();

    user = User.builder()
        .userId(userId)
        .name("tester")
        .email("test@example.com")
        .build();

    template = Template.of(user, "title", "desc", true);
  }

  @Test
  @DisplayName("템플릿 생성 성공")
  void createTemplate_success() {
    TemplateRequest req = new TemplateRequest();
    req.setUserId(userId);
    req.setTitle("title");
    req.setDescription("desc");
    req.setIsPublic(true);

    when(em.getReference(User.class, userId)).thenReturn(user);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(templateRepository.save(any(Template.class))).thenReturn(template);

    TemplateResponse response = templateService.createTemplate(req);

    assertThat(response.getTemplate().getTitle()).isEqualTo("title");
    assertThat(response.getTemplate().getDescription()).isEqualTo("desc");
  }

  @Test
  @DisplayName("템플릿 조회 성공")
  void findById_success() {
    // Given
    when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

    // When
    TemplateResponse response = templateService.findById(templateId, userId);

    // Then
    assertThat(response.getTemplate().getTitle()).isEqualTo("title");
    assertThat(response.getTemplate().getDescription()).isEqualTo("desc");
    assertThat(response.getTemplate().getUser().getUserId()).isEqualTo(userId);
    assertThat(response.getTemplate().getIsPublic()).isTrue();
  }

  @Test
  @DisplayName("템플릿 상세 조회 실패 - 존재하지 않음")
  void findById_notFound() {
    // Given
    when(templateRepository.findById(templateId)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> templateService.findById(templateId, userId))
        .isInstanceOf(CustomException.class)
        .hasMessage(ErrorCode.TEMPLATE_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("템플릿 수정 성공")
  void updateTemplate_success() {
    TemplateRequest req = new TemplateRequest();
    req.setTemplateId(templateId);
    req.setUserId(userId);
    req.setTitle("new title");
    req.setDescription("new desc");
    req.setIsPublic(false);

    // Given
    when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

    // When
    TemplateResponse response = templateService.updateTemplate(req);

    // Then
    assertThat(response.getTemplate().getTitle()).isEqualTo("new title");
    assertThat(response.getTemplate().getIsPublic()).isFalse();
    assertThat(response.getTemplate().getDescription()).isEqualTo("new desc");
    assertThat(response.getTemplate().getUser().getUserId()).isEqualTo(userId);
  }

  @Test
  @DisplayName("템플릿 수정 실패 - 소유자 불일치")
  void updateTemplate_ownerMismatch() {
    TemplateRequest req = new TemplateRequest();
    req.setTemplateId(templateId);
    req.setUserId(UUID.randomUUID()); // 다른 유저
    req.setTitle("new title");
    req.setDescription("new desc");
    req.setIsPublic(true);

    when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

    assertThatThrownBy(() -> templateService.updateTemplate(req))
        .isInstanceOf(CustomException.class)
        .hasMessage(ErrorCode.TEMPLATE_OWNER_MISMATCH.getMessage());
  }

  @Test
  @DisplayName("템플릿 삭제 성공")
  void deleteTemplate_success() {
    when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

    templateService.deleteTemplate(templateId, user.getUserId());

    verify(templateRepository, times(1)).delete(template);
  }

  @Test
  @DisplayName("템플릿 삭제 실패 - 소유자 불일치")
  void deleteTemplate_ownerMismatch() {
    UUID otherUserId = UUID.randomUUID();

    // Given
    when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

    // When & Then
    assertThatThrownBy(() -> templateService.deleteTemplate(templateId, otherUserId))
        .isInstanceOf(CustomException.class)
        .hasMessage(ErrorCode.TEMPLATE_OWNER_MISMATCH.getMessage());
  }
}