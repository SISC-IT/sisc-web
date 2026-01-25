package org.sejongisc.backend.attendance.controller;

import static org.sejongisc.backend.attendance.util.AuthUserUtil.requireUserId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.SessionUserResponse;
import org.sejongisc.backend.attendance.service.SessionUserService;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/attendance/sessions/users")
@Tag(
    name = "세션 유저 API",
    description = "세션 사용자 관련 API"
)
public class SessionUserController {
  private final SessionUserService sessionUserService;
  /**
   * 세션에 사용자 추가 (관리자용)
   * - 사용자를 세션에 추가
   * - 중복 참여 방지
   * - 자동으로 이전 라운드들에 결석 처리
   */
  @Operation(
      summary = "세션에 사용자 추가",
      description = """
                ## 인증(JWT): **필요**

                """
  )
  @PostMapping("/{sessionId}/users")
  public ResponseEntity<SessionUserResponse> addUserToSession(
      @PathVariable UUID sessionId,
      @RequestParam UUID userId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    UUID adminUserId = requireUserId(userDetails);

    SessionUserResponse response = sessionUserService.addUserToSession(sessionId,userId,adminUserId);


    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * 세션에서 사용자 제거 (관리자용)
   * - 사용자를 세션에서 제거
   * - 해당 사용자의 모든 출석 기록도 함께 삭제
   */
  @Operation(
      summary = "세션에서 사용자 제거",
      description = """
                ## 인증(JWT): **필요**

                """
  )
  @DeleteMapping("/{sessionId}/users/{userId}")
  public ResponseEntity<Void> removeUserFromSession(
      @PathVariable UUID sessionId,
      @PathVariable UUID userId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    log.info("세션에서 사용자 제거: 세션ID={}, 사용자ID={}", sessionId, userId);
    UUID adminUserId = requireUserId(userDetails);

    sessionUserService.removeUserFromSession(sessionId, userId, adminUserId);

    log.info("세션에서 사용자 제거 완료: 세션ID={}, 사용자ID={}", sessionId, userId);

    return ResponseEntity.noContent().build();
  }

  /**
   * 세션 참여자 조회
   * - 세션에 참여 중인 모든 사용자 목록
   * - 참여 순서대로 정렬
   */
  @Operation(
      summary = "세션 참여자 조회",
      description = """
                ## 인증(JWT): **필요**

                """
  )
  @GetMapping("/{sessionId}/users")
  public ResponseEntity<List<SessionUserResponse>> getSessionUsers(@PathVariable UUID sessionId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    log.info("세션 참여자 조회: 세션ID={}", sessionId);
    UUID adminUserId = requireUserId(userDetails);

    List<SessionUserResponse> users = sessionUserService.getSessionUsers(sessionId, adminUserId);

    log.info("세션 참여자 조회 완료: 세션ID={}, 참여자 수={}", sessionId, users.size());

    return ResponseEntity.ok(users);
  }


}
