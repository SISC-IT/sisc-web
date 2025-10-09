package org.sejongisc.backend.template.service;


import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.template.dto.TemplateRequest;
import org.sejongisc.backend.template.dto.TemplateResponse;
import org.sejongisc.backend.template.entity.Template;
import org.sejongisc.backend.template.repository.TemplateRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

  private final TemplateRepository templateRepository;
  private final EntityManager em;

  // 유저 ID로 템플릿 목록 조회
  public TemplateResponse findAllByUserId(UUID userId) {
    return TemplateResponse.builder()
        .templates(templateRepository.findAllByUserUserId(userId))
        .build();
  }

  // 템플릿 ID로 템플릿 조회
  public TemplateResponse findById(UUID templateId) {
    // TODO : 백테스트 기록도 같이 조회
    return TemplateResponse.builder()
        .template(
            templateRepository.findById(templateId)
            .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND))
        )
        .build();
  }

  // 템플릿 생성
  public TemplateResponse createTemplate(TemplateRequest request) {
    // userId 만을 가진 FK 전용 프록시 객체 생성
    User userRef = em.getReference(User.class, request.getUserId());

    Template template = Template.of(userRef, request.getTitle(),
        request.getDescription(), request.getIsPublic());

    templateRepository.save(template);

    return TemplateResponse.builder()
        .template(template)
        .build();
  }

  // 템플릿 수정
  public TemplateResponse updateTemplate(TemplateRequest request) {
    Template template = authorizeTemplateOwner(request.getTemplateId(), request.getUserId());

    template.update(request.getTitle(), request.getDescription(), request.getIsPublic());

    templateRepository.save(template);

    return TemplateResponse.builder()
        .template(template)
        .build();
  }

  // 템플릿 삭제
  public void deleteTemplate(UUID templateId, UUID userId) {
    Template template = authorizeTemplateOwner(templateId, userId);
    // TODO : 좋아요 / 북마크 삭제
    templateRepository.delete(template);
  }

  private Template authorizeTemplateOwner(UUID templateId, UUID userId) {
    Template template = templateRepository.findById(templateId)
            .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND));
    if (!template.getUser().getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.TEMPLATE_OWNER_MISMATCH);
    }
    return template;
  }
}
