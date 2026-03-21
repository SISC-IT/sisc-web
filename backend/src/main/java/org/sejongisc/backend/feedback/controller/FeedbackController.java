package org.sejongisc.backend.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.feedback.dto.FeedbackCreateRequest;
import org.sejongisc.backend.feedback.service.FeedbackService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/feedbacks")
@Tag(name = "03. 사용자 피드백 API", description = "사용자 피드백 등록 API")
public class FeedbackController {

  private final FeedbackService feedbackService;

  @Operation(summary = "피드백 등록", description = """
  로그인한 사용자의 피드백을 저장합니다.
  관리자에게 전달되어 서비스 개선에 활용됩니다.
  유저 정보는 저장되지 않습니다.
  """)
  @PostMapping
  public ResponseEntity<Void> createFeedback(@RequestBody FeedbackCreateRequest request) {
    feedbackService.createFeedback(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }
}
