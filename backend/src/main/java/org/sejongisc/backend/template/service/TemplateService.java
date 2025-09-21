package org.sejongisc.backend.template.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.template.dto.TemplateRequest;
import org.sejongisc.backend.template.dto.TemplateResponse;
import org.sejongisc.backend.template.entity.Template;
import org.sejongisc.backend.template.repository.TemplateRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

  private final TemplateRepository templateRepository;

  // 유저 ID로 템플릿 목록 조회
  public TemplateResponse findAllByUserId(UUID userId) {
    return TemplateResponse.builder()
        .templates(templateRepository.findAllByUserUserId(userId))
        .build();
  }

  // 템플릿 ID로 템플릿 조회
  public TemplateResponse findById(UUID templateId) {
    return TemplateResponse.builder()
        .template(
            templateRepository.findById(templateId)
            .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND))
        )
        .build();
  }

  // 템플릿 생성
  public TemplateResponse createTemplate(TemplateRequest templateRequest) {
    Template template = Template.createTemplateFromTemplateRequest(templateRequest);
    templateRepository.save(template);

    return TemplateResponse.builder()
        .template(template)
        .build();
  }

  // 템플릿 수정
  public TemplateResponse updateTemplate(TemplateRequest templateRequest) {
    Template template = templateRepository.findById(templateRequest.getTemplateId())
            .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND));
    // TODO : 권한 검사
    template.updateFromTemplateRequest(templateRequest);

    templateRepository.save(template);

    return TemplateResponse.builder()
        .template(template)
        .build();
  }

  // 템플릿 삭제
  public void deleteTemplate(UUID templateId) {
    Template template = templateRepository.findById(templateId)
            .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND));

    // TODO : 권한 검사, 좋아요 / 북마크 삭제
    templateRepository.delete(template);
  }
}
