package org.sejongisc.backend.feedback.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.feedback.dto.FeedbackCreateRequest;
import org.sejongisc.backend.feedback.entity.Feedback;
import org.sejongisc.backend.feedback.repository.FeedbackRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

  private final FeedbackRepository feedbackRepository;

  @Transactional
  public void createFeedback(UUID userId, FeedbackCreateRequest request) {
    String content = request.content();
    if (content == null || content.isBlank()) {
      throw new CustomException(ErrorCode.FEEDBACK_CONTENT_REQUIRED);
    }
    feedbackRepository.save(Feedback.builder()
        .content(content.trim())
        .build());

    log.info("피드백 저장 완료: userId={}", userId);
  }

  @Transactional(readOnly = true)
  public Page<Feedback> getFeedbacks(Pageable pageable) {
    return feedbackRepository.findAll(pageable);
  }
}
