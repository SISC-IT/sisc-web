package org.sejongisc.backend.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.feedback.service.FeedbackService;
import org.sejongisc.backend.feedback.entity.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/feedbacks")
@Tag(name = "00. 관리자 피드백 API", description = "관리자용 피드백 조회 API")
public class AdminFeedbackController {

  private final FeedbackService feedbackService;

  @Operation(summary = "피드백 목록 조회", description = "관리자 화면에서 사용자 피드백을 최신순으로 조회합니다.")
  @GetMapping
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<Page<Feedback>> getFeedbacks(
      @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable
  ) {
    return ResponseEntity.ok(feedbackService.getFeedbacks(pageable));
  }
}
