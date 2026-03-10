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
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/attendance/sessions")
@Tag(
    name = "06. 세션 유저 API",
    description = "세션 사용자 관련 API"
)
public class SessionUserController {

  private final SessionUserService sessionUserService;

  /**
   * 세션에 사용자 추가 (관리자용) - 사용자를 세션에 추가 - 중복 참여 방지 - 자동으로 이전 라운드들에 결석 처리
   */
  @Operation(
      summary = "세션에 사용자 추가",
      description = """
      ## 인증(JWT)
      - **필요**
      
      ## 권한
      - **세션 OWNER**
      
      ## 경로 파라미터
      - **`sessionId`**: 사용자를 추가할 세션 ID (`UUID`)
      
      ## 쿼리 파라미터
      - **`userId`**: 추가할 대상 사용자의 ID (`UUID`)
      
      ## 동작 설명
      - 특정 사용자를 세션의 참여자(`PARTICIPANT`)로 추가
      - 세션 중간 참여 시, 오늘 이전의 모든 라운드에 대해 자동으로 **결석** 처리 및 사유("세션 중간 참여 - 이전 라운드는 자동 결석 처리") 등록
      
      ## 에러 코드
      - **`SESSION_NOT_FOUND`**: 해당 출석 세션이 존재하지 않습니다.
      - **`USER_NOT_FOUND`**: 유저를 찾을 수 없습니다.
      - **`ALREADY_JOINED`**: 이미 출석 세션에 참여 중입니다.
      - **`NOT_SESSION_OWNER`**: 세션 소유자 권한이 없습니다.
      
      """)
  @PostMapping("/{sessionId}/users")
  public ResponseEntity<SessionUserResponse> addUserToSession(
      @PathVariable UUID sessionId,
      @RequestParam UUID userId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UUID adminUserId = requireUserId(userDetails);
    SessionUserResponse response = sessionUserService.addUserToSession(sessionId, userId, adminUserId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * 세션에서 사용자 제거 (관리자용) - 사용자를 세션에서 제거 - 해당 사용자의 모든 출석 기록도 함께 삭제
   */
  @Operation(
      summary = "세션에서 사용자 제거",
      description = """
      ## 인증(JWT)
      - **필요**
      
      ## 권한
      - **세션 OWNER**
      
      ## 경로 파라미터
      - **`sessionId`**: 사용자를 제거할 세션 ID (`UUID`)
      - **`userId`**: 제거할 대상 사용자의 ID (`UUID`)
      
      ## 동작 설명
      - 세션에서 특정 사용자를 제거
      - 해당 사용자가 이 세션에서 가졌던 모든 출석 기록(`Attendance`)을 함께 삭제
      
      ## 에러 코드
      - **`SESSION_NOT_FOUND`**: 해당 출석 세션이 존재하지 않습니다.
      - **`NOT_SESSION_OWNER`**: 세션 소유자 권한이 없습니다.
      
      """)
  @DeleteMapping("/{sessionId}/users/{userId}")
  public ResponseEntity<Void> removeUserFromSession(
      @PathVariable UUID sessionId,
      @PathVariable UUID userId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UUID adminUserId = requireUserId(userDetails);
    sessionUserService.removeUserFromSession(sessionId, userId, adminUserId);
    return ResponseEntity.noContent().build();
  }

  /**
   * 세션 참여자 조회 - 세션에 참여 중인 모든 사용자 목록 - 참여 순서대로 정렬
   */
  @Operation(
      summary = "세션 참여자 조회",
      description = """
      ## 인증(JWT)
      - **필요**
      
      ## 권한
      - **세션 MEMBER** (OWNER, MANAGER, PARTICIPANT 모두 가능)
      
      ## 경로 파라미터
      - **`sessionId`**: 참여자 목록을 조회할 세션 ID (`UUID`)
      
      ## 동작 설명
      - 세션에 참여 중인 모든 사용자 목록을 조회
      - 해당 세션의 멤버가 아닌 경우 조회 불가
      
      ## 에러 코드
      - **`NOT_SESSION_MEMBER`**: 출석 세션의 멤버가 아닙니다.
      
      """)
  @GetMapping("/{sessionId}/users")
  public ResponseEntity<List<SessionUserResponse>> getSessionUsers(
      @PathVariable UUID sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UUID adminUserId = requireUserId(userDetails);
    List<SessionUserResponse> users = sessionUserService.getSessionUsers(sessionId, adminUserId);
    return ResponseEntity.ok(users);
  }

  /**
   * 정규 세션 용 전체 회원 넣는 API(회장용)
   */
  @Operation(
      summary = "정규세션에 active 상태인 전체 회원 추가",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 ( `sessionId` )
          
          ## 회장이면서 세션의 장이어야만 가능
          """
  )
  @PostMapping("/{sessionId}/users/add-all")
  @PreAuthorize("hasRole('PRESIDENT')")
  public ResponseEntity<Void> addAllUsers(
      @PathVariable UUID sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID adminUserId = requireUserId(userDetails);
    sessionUserService.addAllUsers(sessionId, adminUserId);
    return ResponseEntity.ok().build();
  }

  /**
   * 세션 관리자(MANAGER) 권한 부여
   */
  @Operation(
      summary = "세션 관리자 추가",
      description = """
    ## 권한
    - **세션 OWNER**
    
    ## 동작 설명
    - 특정 사용자의 역할을 `MANAGER`로 격상시킵니다.
    """)
  @PostMapping("/{sessionId}/admins/{userId}")
  public ResponseEntity<Void> addAdminToSession(
      @PathVariable UUID sessionId,
      @PathVariable UUID userId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    UUID adminUserId = requireUserId(userDetails);
    // 서비스단에서 세션 소유자(OWNER)인지 검증하는 로직이 포함되어야 함
    sessionUserService.addAdmin(sessionId, userId, adminUserId);
    return ResponseEntity.ok().build();
  }

  /**
   * 세션 관리자(MANAGER) 권한 해제
   */
  @Operation(
      summary = "세션 관리자 제거",
      description = """
    ## 권한
    - **세션 OWNER**
    
    ## 동작 설명
    - 특정 사용자의 역할을 `PARTICIPANT`로 강등시킵니다.
    - `OWNER`는 강등될 수 없습니다.
    """)
  @DeleteMapping("/{sessionId}/admins/{userId}")
  public ResponseEntity<Void> removeAdminFromSession(
      @PathVariable UUID sessionId,
      @PathVariable UUID userId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    UUID adminUserId = requireUserId(userDetails);
    sessionUserService.removeAdmin(sessionId, userId, adminUserId);
    return ResponseEntity.noContent().build();
  }
}
