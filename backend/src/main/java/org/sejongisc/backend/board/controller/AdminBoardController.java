package org.sejongisc.backend.board.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.dto.BoardRequest;
import org.sejongisc.backend.board.service.AdminBoardService;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/board/admin")
@Tag(
    name = "게시판 관리 API",
    description = "게시판 생성 및 삭제 관련 API 제공"
)
public class AdminBoardController {

  private final AdminBoardService adminBoardService;

  // 게시판 생성
  @Operation(
      summary = "게시판 생성",
      description = "게시판 이름과 상위 게시판 ID를 포함한 새로운 게시판을 생성합니다."
                    + "상위 게시판의 ID가 null 이면 최상위 게시판으로 생성됩니다."
                    + "회장만 삭제할 수 있습니다."
  )
  @PostMapping
  public ResponseEntity<Void> createBoard(
      @RequestBody @Valid BoardRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    UUID userId = customUserDetails.getUserId();
    adminBoardService.createBoard(request, userId);
    return ResponseEntity.ok().build();
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
    adminBoardService.deleteBoard(boardId, userId);
    return ResponseEntity.ok("게시판 삭제가 완료되었습니다.");
  }
}
