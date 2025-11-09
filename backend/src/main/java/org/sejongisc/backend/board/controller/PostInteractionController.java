package org.sejongisc.backend.board.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.dto.CommentRequest;
import org.sejongisc.backend.board.service.PostInteractionService;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/post")
@Tag(
    name = "게시물 기능 관련 API",
    description = "댓글 작성, 수정, 삭제 및 좋아요, 북마크 API 제공"
)
public class PostInteractionController {

  private final PostInteractionService postInteractionService;

  // 좋아요 토글
  @PostMapping("/{postId}/like")
  public ResponseEntity<Void> toggleLike(
      @PathVariable UUID postId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postInteractionService.toggleLike(postId, userId);
    return ResponseEntity.ok().build();
  }

  // 북마크 토글
  @PostMapping("/{postId}/bookmark")
  public ResponseEntity<Void> toggleBookmark(
      @PathVariable UUID postId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postInteractionService.toggleBookmark(postId, userId);
    return ResponseEntity.ok().build();
  }

  // 댓글 작성
  @PostMapping("/{postId}/comment")
  public ResponseEntity<Void> createComment(
      @RequestBody CommentRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postInteractionService.createComment(request, userId);
    return ResponseEntity.ok().build();
  }

  // 댓글 수정
  @PutMapping("/comment/{commentId}")
  public void updateComment(
      @PathVariable UUID commentId,
      @RequestBody CommentRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postInteractionService.updateComment(request, commentId, userId);
  }

  // 댓글 삭제
  @DeleteMapping("/comment/{commentId}")
  public void deleteComment(
      @PathVariable UUID commentId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postInteractionService.deleteComment(commentId, userId);
  }
}
