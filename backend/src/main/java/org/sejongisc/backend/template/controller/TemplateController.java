package org.sejongisc.backend.template.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.template.dto.TemplateRequest;
import org.sejongisc.backend.template.dto.TemplateResponse;
import org.sejongisc.backend.template.service.TemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/backtest/templates")
@Tag(
    name = "템플릿 API",
    description = "백테스트 템플릿 관련 API 제공"
)
public class TemplateController {
  private final TemplateService templateService;

  // 템플릿 목록 조회
  @GetMapping
  @Operation(
      summary = "템플릿 목록 조회",
      description = "사용자의 백테스트 템플릿 목록을 조회합니다."
  )
  public ResponseEntity<TemplateResponse> getTemplateList(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(templateService.findAllByUserId(customUserDetails.getUserId()));
  }

  // 템플릿 상세 조회
  @GetMapping("/{templateId}")
  @Operation(
      summary = "템플릿 상세 조회",
      description = "지정된 템플릿 ID에 대한 상세 정보 및 템플릿에 저장된 백테스트 실행 기록들을 조회합니다."
  )
  public ResponseEntity<TemplateResponse> getTemplateById(@PathVariable UUID templateId,
                                                          @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(templateService.findById(templateId, customUserDetails.getUserId()));
  }

  // 템플릿 생성
  @PostMapping
  @Operation(
      summary = "템플릿 생성",
      description = "새로운 백테스트 템플릿을 생성합니다."
  )
  public ResponseEntity<TemplateResponse> createTemplate(@RequestBody TemplateRequest request,
                                                         @AuthenticationPrincipal CustomUserDetails customUserDetail) {
    request.setUserId(customUserDetail.getUserId());
    return ResponseEntity.ok(templateService.createTemplate(request));
  }

  // 템플릿 수정
  @PatchMapping("/{templateId}")
  @Operation(
      summary = "템플릿 수정",
      description = "기존의 백테스트 템플릿을 수정합니다."
  )
  public ResponseEntity<TemplateResponse> updateTemplate(@RequestBody TemplateRequest request,
                                                         @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(templateService.updateTemplate(request));
  }

  // 템플릿 삭제
  @DeleteMapping("/{templateId}")
  @Operation(
      summary = "템플릿 삭제",
      description = "지정된 템플릿 ID에 대한 템플릿을 삭제합니다."
  )
  public ResponseEntity<Void> deleteTemplate(@PathVariable UUID templateId,
                                             @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    templateService.deleteTemplate(templateId, customUserDetails.getUserId());
    return ResponseEntity.noContent().build();
  }
}
