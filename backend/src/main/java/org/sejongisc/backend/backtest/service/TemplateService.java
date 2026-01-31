package org.sejongisc.backend.backtest.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.backtest.repository.BacktestRunRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.backtest.dto.TemplateRequest;
import org.sejongisc.backend.backtest.dto.TemplateResponse;
import org.sejongisc.backend.backtest.entity.Template;
import org.sejongisc.backend.backtest.repository.TemplateRepository;
import org.sejongisc.backend.user.repository.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

  private final TemplateRepository templateRepository;
  private final BacktestRunRepository backtestRunRepository;
  private final UserRepository userRepository;

  // 유저 ID로 템플릿 목록 조회
  public TemplateResponse findAllByUserId(UUID userId) {
    return TemplateResponse.builder()
        .templates(templateRepository.findAllByUserUserId(userId))
        .build();
  }

  // 템플릿 ID로 템플릿 상세 및 백테스트 리스트 조회
  public TemplateResponse findById(UUID templateId, UUID userId) {
    Template template = authorizeTemplateOwner(templateId, userId);
    // TODO : 공개 템플릿 접근 허용 로직 추가 필요
    return TemplateResponse.builder()
        .template(template)
        .backtestRuns(backtestRunRepository.findByTemplate_TemplateIdWithTemplate(templateId))
        .build();
  }

  // 템플릿 생성
  public TemplateResponse createTemplate(TemplateRequest request) {
    User user = userRepository.findById(request.getUserId())
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    Template template = Template.of(user, request.getTitle(),
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
