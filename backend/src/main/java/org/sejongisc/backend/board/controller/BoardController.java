package org.sejongisc.backend.board.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.dto.BoardResponse;
import org.sejongisc.backend.board.dto.CommentRequest;
import org.sejongisc.backend.board.dto.PostRequest;
import org.sejongisc.backend.board.dto.PostResponse;
import org.sejongisc.backend.board.service.PostInteractionService;
import org.sejongisc.backend.board.service.PostService;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/board")
@Tag(
    name = "07. 게시판 및 게시물 API",
    description = "게시판 및 게시물 작성, 수정, 삭제 관련 API 제공"
)
public class BoardController {

  private final PostService postService;
  private final PostInteractionService postInteractionService;

  // 게시물 생성
  @Operation(
      summary = "게시물 작성",
      description = "게시판 ID, 제목, 내용, 첨부파일을 포함한 게시물 생성"
                    + "anonymous 값을 true로 보내면 익명 게시물 작성됨"
                    + "값을 보내지 않으면 기본값(false) 으로 설정됨"
  )
  @PostMapping(value = "/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> createPost(
      @Valid @ModelAttribute PostRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.savePost(request, userId);
    return ResponseEntity.ok().build();
  }

  // 게시물 수정
  @Operation(
      summary = "게시물 수정",
      description = "제목, 내용, 첨부파일을 포함한 게시물 수정"
                    + "anonymous 값을 통해 익명 여부도 변경할 수 있음 "
                    + "첨부파일은 전체 교체 방식으로 처리되며, 게시판 종류는 변경할 수 없음"
  )
  @PutMapping(value = "/post/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> updatePost(
      @Valid @ModelAttribute PostRequest request,
      @PathVariable UUID postId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.updatePost(request, postId, userId);
    return ResponseEntity.ok().build();
  }

  // 게시물 삭제
  @Operation(
      summary = "게시물 삭제",
      description = "게시물 ID를 통해 게시물 삭제"
                    + "작성자 본인만 삭제할 수 있으며, 관련 첨부파일과 댓글 정보도 함께 삭제"
  )
  @DeleteMapping("/post/{postId}")
  public void deletePost(
      @PathVariable UUID postId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.deletePost(postId, userId);
  }

  // 게시물 조회
  @Operation(
      summary = "게시물 조회",
      description = "게시판 ID를 통해 해당 게시판의 게시물 목록 조회"
                    + "pageNumber와 pageSize로 페이징할 수 있으며 기본값은 각각 0, 20"
  )
  @GetMapping("/posts")
  public ResponseEntity<Page<PostResponse>> getPosts(
      @RequestParam UUID boardId,
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "20") int pageSize,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    return ResponseEntity.ok(postService.getPosts(boardId, userId, pageNumber, pageSize));
  }

  // 게시물 검색
  @Operation(
      summary = "게시물 검색",
      description = "게시판 ID와 검색어를 기준으로 제목 또는 내용에 검색어가 포함된 게시물 조회 "
                    + "pageNumber와 pageSize로 페이징할 수 있으며 기본값은 각각 0, 20"
  )
  @GetMapping("/posts/search")
  public ResponseEntity<Page<PostResponse>> searchPosts(
      @RequestParam UUID boardId,
      @RequestParam String keyword,
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "20") int pageSize,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    return ResponseEntity.ok(postService.searchPosts(boardId, userId, keyword, pageNumber, pageSize));
  }

  // 게시물 상세 조회
  @Operation(
      summary = "게시물 상세 조회",
      description = "게시물 ID를 통해 게시물의 상세 정보와 댓글 목록 조회 "
                    + "익명 게시물과 익명 댓글은 작성자 정보가 닉네임은 \"익명\" 그 외 회원정보는 null로 반환됨"
                    + "댓글은 commentPageNumber와 commentPageSize로 페이징할 수 있으며 기본값은 각각 0, 20"
  )
  @GetMapping("/post/{postId}")
  public ResponseEntity<PostResponse> getPostDetail(
      @PathVariable UUID postId,
      @RequestParam(defaultValue = "0") int commentPageNumber,
      @RequestParam(defaultValue = "20") int commentPageSize,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    PostResponse response = postService.getPostDetail(postId, userId, commentPageNumber, commentPageSize);
    return ResponseEntity.ok(response);
  }

  // 최상위 게시판 목록 조회
  @Operation(
      summary = "부모 게시판 목록 조회",
      description = "최상위 부모 게시판 목록 조회"
  )
  @GetMapping("/parents")
  public ResponseEntity<List<BoardResponse>> getParentBoards(
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(postService.getParentBoards());
  }

  // 하위 게시판 목록 조회
  @Operation(
      summary = "하위 게시판 목록 조회",
      description = "하위 게시판 목록 조회"
  )
  @GetMapping("/childs")
  public ResponseEntity<List<BoardResponse>> getChildBoards(
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(postService.getChildBoards());
  }

  // 좋아요 토글
  @Operation(
      summary = "좋아요 등록 및 취소",
      description = "게시물 좋아요를 토글"
                    + "이미 좋아요를 누른 게시물이면 취소되고, 누르지 않은 게시물이면 등록"
  )
  @PostMapping("/{postId}/like")
  public ResponseEntity<Void> toggleLike(
      @PathVariable UUID postId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postInteractionService.toggleLike(postId, userId);
    return ResponseEntity.ok().build();
  }

  // 북마크 토글
  @Operation(
      summary = "북마크 등록 및 취소",
      description = "게시물 북마크를 토글 "
                    + "이미 북마크한 게시물이면 취소되고, 북마크하지 않은 게시물이면 등록"
  )
  @PostMapping("/{postId}/bookmark")
  public ResponseEntity<Void> toggleBookmark(
      @PathVariable UUID postId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postInteractionService.toggleBookmark(postId, userId);
    return ResponseEntity.ok().build();
  }

  // 댓글 생성
  @Operation(
      summary = "댓글 작성",
      description = "게시물에 댓글 작성"
                    + "anonymous 값을 true로 보내면 작성자 정보가 익명으로 표시 "
                    + "parentCommentId가 있으면 대댓글로 작성되고, 없으면 일반 댓글로 작성"
  )
  @PostMapping("/comment")
  public ResponseEntity<Void> createComment(
      @RequestBody CommentRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postInteractionService.createComment(request, userId);
    return ResponseEntity.ok().build();
  }

  // 댓글 수정
  @Operation(
      summary = "댓글 수정",
      description = "댓글 내용을 수정합니다. 작성자 본인만 수정할 수 있으며, "
                    + "content와 anonymous 값을 함께 변경할 수 있음"
  )
  @PutMapping("/comment/{commentId}")
  public void updateComment(
      @PathVariable UUID commentId,
      @RequestBody CommentRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postInteractionService.updateComment(request, commentId, userId);
  }

  // 댓글 삭제
  @Operation(
      summary = "댓글 삭제",
      description = "댓글 삭제"
                    + "작성자 본인과 관리자만 삭제할 수 있으며, 대댓글이 있는 경우 함께 삭제됨"
  )
  @DeleteMapping("/comment/{commentId}")
  public void deleteComment(
      @PathVariable UUID commentId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postInteractionService.deleteComment(commentId, userId);
  }
}
