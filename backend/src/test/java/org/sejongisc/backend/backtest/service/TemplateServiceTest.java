package org.sejongisc.backend.backtest.service;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sejongisc.backend.backtest.dto.TemplateRequest;
import org.sejongisc.backend.backtest.dto.TemplateResponse;
import org.sejongisc.backend.backtest.entity.Template;
import org.sejongisc.backend.backtest.repository.BacktestRunRepository;
import org.sejongisc.backend.backtest.repository.TemplateRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.repository.UserRepository;

import java.util.Collections;
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
    private BacktestRunRepository backtestRunRepository;

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
        TemplateRequest req = new TemplateRequest("title", "desc", true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(templateRepository.save(any(Template.class))).thenReturn(template);

        TemplateResponse response = templateService.createTemplate(req, userId);

        assertThat(response.template().getTitle()).isEqualTo("title");
        assertThat(response.template().getDescription()).isEqualTo("desc");
    }

    @Test
    @DisplayName("템플릿 조회 성공")
    void findById_success() {
        // Given
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(backtestRunRepository.findByTemplate_TemplateIdWithTemplate(templateId)).thenReturn(Collections.emptyList());

        // When
        TemplateResponse response = templateService.findById(templateId, userId);

        // Then
        assertThat(response.template().getTitle()).isEqualTo("title");
        assertThat(response.template().getUser().getUserId()).isEqualTo(userId);
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
        // [변경] Record 생성자
        TemplateRequest req = new TemplateRequest("new title", "new desc", false);

        // Given
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

        // When
        TemplateResponse response = templateService.updateTemplate(templateId, userId, req);

        // Then
        assertThat(response.template().getTitle()).isEqualTo("new title");
        assertThat(response.template().getIsPublic()).isFalse();
        assertThat(response.template().getDescription()).isEqualTo("new desc");
    }

    @Test
    @DisplayName("템플릿 수정 실패 - 소유자 불일치")
    void updateTemplate_ownerMismatch() {
        TemplateRequest req = new TemplateRequest("new title", "new desc", true);
        UUID otherUserId = UUID.randomUUID();

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> templateService.updateTemplate(templateId, otherUserId, req))
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