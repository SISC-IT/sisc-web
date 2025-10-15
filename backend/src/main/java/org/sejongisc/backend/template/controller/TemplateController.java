package org.sejongisc.backend.template.controller;

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
public class TemplateController {
  private final TemplateService templateService;


  // 템플릿 목록 조회
  @GetMapping
  public ResponseEntity<TemplateResponse> getTemplateList(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(templateService.findAllByUserId(customUserDetails.getUserId()));
  }

  // 템플릿 상세 조회
  @GetMapping("/{templateId}")
  public ResponseEntity<TemplateResponse> getTemplateById(@PathVariable UUID templateId,
                                                          @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(templateService.findById(templateId));
  }

  // 템플릿 생성
  @PostMapping
  public ResponseEntity<TemplateResponse> createTemplate(@RequestBody TemplateRequest request,
                                                         @AuthenticationPrincipal CustomUserDetails customUserDetail) {
    request.setUserId(customUserDetail.getUserId());
    return ResponseEntity.ok(templateService.createTemplate(request));
  }

  // 템플릿 수정
  @PatchMapping("/{templateId}")
  public ResponseEntity<TemplateResponse> updateTemplate(@RequestBody TemplateRequest request,
                                                         @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(templateService.updateTemplate(request));
  }

  // 템플릿 삭제
  @DeleteMapping("/{templateId}")
  public ResponseEntity<Void> deleteTemplate(@PathVariable UUID templateId,
                                             @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    templateService.deleteTemplate(templateId, customUserDetails.getUserId());
    return ResponseEntity.noContent().build();
  }
}
