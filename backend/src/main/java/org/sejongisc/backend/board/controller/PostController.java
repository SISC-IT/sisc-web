package org.sejongisc.backend.board.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.entity.BoardType;
import org.sejongisc.backend.board.entity.PostType;
import org.sejongisc.backend.board.dto.*;
import org.sejongisc.backend.board.service.PostService;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/post")
@Tag(
    name = "게시글 및 댓글 API",
    description = "게시글 및 댓글 작성, 수정, 삭제 관련 API 제공"
)
public class PostController {

  private final PostService postService;

  // 게시글 작성
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> createPost(
      @Valid @ModelAttribute PostRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.savePost(request, userId);
    return ResponseEntity.ok().build();
  }

  // 게시글 수정
  @PutMapping(value = "/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> updatePost(
      @Valid @ModelAttribute PostRequest request,
      @PathVariable UUID postId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.updatePost(request, postId, userId);
    return ResponseEntity.ok().build();
  }

  // 게시글 삭제
  @DeleteMapping("/{postId}")
  public void deletePost(
      @PathVariable UUID postId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.deletePost(postId, userId);
  }

  // 게시글 조회 (공지/일반)
  @GetMapping
  public ResponseEntity<Page<PostResponse>> getPosts(
      @RequestParam BoardType boardType,
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ResponseEntity.ok(postService.getPosts(boardType, pageNumber, pageSize));
  }

  // 게시글 검색
  @GetMapping("/search")
  public ResponseEntity<Page<PostResponse>> searchPosts(
      @RequestParam String keyword,
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ResponseEntity.ok(postService.searchPosts(keyword, pageNumber, pageSize));
  }

  // 게시물 상세 조회
  @GetMapping("/{postId}")
  public ResponseEntity<PostResponse> getPostDetail(
      @PathVariable UUID postId,
      @RequestParam(defaultValue = "0") int commentPageNumber,
      @RequestParam(defaultValue = "20") int commentPageSize) {
    PostResponse response = postService.getPostDetail(postId, commentPageNumber, commentPageSize);
    return ResponseEntity.ok(response);
  }

  // 좋아요 토글
  @PostMapping("/{postId}/like")
  public ResponseEntity<Void> toggleLike(
      @PathVariable UUID postId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.toggleLike(postId, userId);
    return ResponseEntity.ok().build();
  }

  // 북마크 토글
  @PostMapping("/{postId}/bookmark")
  public ResponseEntity<Void> toggleBookmark(
      @PathVariable UUID postId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.toggleBookmark(postId, userId);
    return ResponseEntity.ok().build();
  }

  // 댓글 작성
  @PostMapping("/{postId}/comment")
  public ResponseEntity<Void> createComment(
      @RequestBody CommentRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.createComment(request, userId);
    return ResponseEntity.ok().build();
  }

  // 댓글 수정
  @PutMapping("/comment/{commentId}")
  public void updateComment(
      @PathVariable UUID commentId,
      @RequestBody CommentRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.updateComment(request, commentId, userId);
  }

  // 댓글 삭제
  @DeleteMapping("/comment/{commentId}")
  public void deleteComment(
      @PathVariable UUID commentId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.deleteComment(commentId, userId);
  }
}
