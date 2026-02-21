package org.sejongisc.backend.board.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.dto.BoardRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

  // 게시글 작성
  @Operation(
      summary = "게시물 작성",
      description = "게시판 ID, 제목, 내용, 첨부파일을 포함한 게시물을 작성합니다."
  )
  @PostMapping(value = "/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> createPost(
      @Valid @ModelAttribute PostRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.savePost(request, userId);
    return ResponseEntity.ok().build();
  }

  // 게시글 수정
  @Operation(
      summary = "게시물 수정",
      description = "제목, 내용, 첨부파일을 포함한 게시물을 수정합니다."
                    + "첨부파일은 전체 파일 삭제 후 재저장 방식으로 이루어집니다."
                    + "게시판 종류는 수정할 수 없습니다."
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

  // 게시글 삭제
  @Operation(
      summary = "게시글 삭제",
      description = "게시글 ID를 통해 게시글을 삭제합니다."
                    + "작성자 본인만 삭제할 수 있습니다."
                    + "관련 첨부파일 및 댓글 등도 함께 삭제됩니다."
  )
  @DeleteMapping("/post/{postId}")
  public void deletePost(
      @PathVariable UUID postId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.deletePost(postId, userId);
  }

  // 게시글 조회
  @Operation(
      summary = "게시글 조회",
      description = "게시판 ID를 통해 해당 게시판의 게시글 목록을 조회합니다."
                    + "페이지 번호와 페이지 크기를 통해 페이징 처리가 가능합니다."
                    + "기본값은 페이지 번호 0, 페이지 크기 20입니다."
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

  // 게시글 검색
  @Operation(
      summary = "게시글 검색",
      description = "게시판 ID와 키워드를 통해 해당 게시판의 게시글 목록을 검색합니다."
                    + "페이지 번호와 페이지 크기를 통해 페이징 처리가 가능합니다."
                    + "기본값은 페이지 번호 0, 페이지 크기 20입니다."
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
      description = "게시물 ID를 통해 게시물의 상세 정보를 조회합니다."
                    + "댓글에 대해서도 페이지 번호와 페이지 크기를 통해 페이징 처리가 가능합니다."
                    + "기본값은 댓글 페이지 번호 0, 댓글 페이지 크기 20입니다."
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

  // 게시판 생성
  @Operation(
      summary = "게시판 생성",
      description = "게시판 이름과 상위 게시판 ID를 포함한 새로운 게시판을 생성합니다."
                    + "상위 게시판의 ID가 null 이면 최상위 게시판으로 생성됩니다."
  )
  @PostMapping
  public ResponseEntity<Void> createBoard(
      @RequestBody @Valid BoardRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.createBoard(request, userId);
    return ResponseEntity.ok().build();
  }

  // 최상위 게시판 목록 조회
  @Operation(
      summary = "부모 게시판 목록 조회",
      description = "최상위 부모 게시판들의 목록을 조회합니다."
  )
  @GetMapping("/parents")
  public ResponseEntity<List<BoardResponse>> getParentBoards(
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(postService.getParentBoards());
  }


  // 하위 게시판 목록 조회
  @Operation(
          summary = "하위 게시판 목록 조회",
          description = "하위 게시판 목록을 조회합니다."
  )
  @GetMapping("/childs")
  public ResponseEntity<List<BoardResponse>> getChildBoards(
          @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(postService.getChildBoards());
  }

  // 게시판 삭제
  @Operation(
          summary = "게시판 삭제",
          description = "게시판 ID를 통해 게시판을 삭제합니다."
                  + "회장만 삭제할 수 있습니다."
                  + "관련 첨부파일 및 댓글 등도 함께 삭제됩니다."
  )
  @DeleteMapping("/{boardId}")
  public ResponseEntity<?> deleteBoard(
          @PathVariable UUID boardId,
          @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postService.deleteBoard(boardId, userId);
    return ResponseEntity.ok("게시판 삭제가 완료되었습니다.");
  }

  // 좋아요 토글
  @Operation(
      summary = "좋아요 등록 및 취소",
      description = "좋아요를 등록하거나 취소합니다. "
                    + "이미 좋아요를 한 게시물인 경우 좋아요가 취소되고, "
                    + "좋아요를 하지 않은 게시물인 경우 좋아요가 등록됩니다."
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
      description = "북마크를 등록하거나 취소합니다. "
                    + "이미 북마크를 한 게시물인 경우 북마크가 취소되고, "
                    + "북마크를 하지 않은 게시물인 경우 북마크가 등록됩니다."
  )
  @PostMapping("/{postId}/bookmark")
  public ResponseEntity<Void> toggleBookmark(
      @PathVariable UUID postId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postInteractionService.toggleBookmark(postId, userId);
    return ResponseEntity.ok().build();
  }

  // 댓글 작성
  @Operation(
      summary = "댓글 작성",
      description = "게시물에 댓글을 작성합니다."
                    + "parentCommentId가 제공되면 해당 댓글에 대한 대댓글로 작성됩니다."
                    + "null일 경우 일반 댓글로 작성됩니다."
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
      description = "댓글을 수정합니다. 작성자 본인만 수정할 수 있습니다."
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
      description = "댓글을 삭제합니다. "
                    + "작성자 본인과 관리자만 삭제할 수 있습니다."
                    + "대댓글이 있는 경우 함께 삭제됩니다."
  )
  @DeleteMapping("/comment/{commentId}")
  public void deleteComment(
      @PathVariable UUID commentId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    postInteractionService.deleteComment(commentId, userId);
  }



}
