package org.sejongisc.backend.attendance.controller;

import static org.sejongisc.backend.attendance.util.AuthUserUtil.requireUserId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceSessionRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionResponse;
import org.sejongisc.backend.attendance.service.AttendanceSessionService;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/attendance/sessions")
@Slf4j
@Tag(
    name = "출석 세션 API",
    description = "출석 세션 생성, 조회, 수정, 삭제 및 상태 관리 관련 API"
)
public class AttendanceSessionController {

  private final AttendanceSessionService attendanceSessionService;

  /**
   * 출석 세션 생성
   */
  @Operation(
      summary = "출석 세션 생성",
      description = """
          
          ## 인증(JWT): **필요**
          
          
          ## 요청 파라미터 ( `AttendanceSessionRequest` )
          - **`title`**: 세션 제목
          - **`description`**: 세션 설명
          - **`allowedMinutes`**: 체크인 허용 시간 (분)
          - **`status`**: 세션 상태
          
          ## 반환값 없음
          """
  )
  @PostMapping
  public ResponseEntity<Void> createSession(@AuthenticationPrincipal(expression = "userId") UUID userId,
      @RequestBody AttendanceSessionRequest request) {
    attendanceSessionService.createSession(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /**
   * 세션 상세 조회 - 세션 ID로 상세 정보 조회
   */
  @Operation(
      summary = "세션 상세 조회",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 ( `sessionId` )
          
          ## 반환값 (`AttendanceSessionResponse`)
          - **`sessionId`**: 세션 ID
          - **`title`**: 세션 제목
          - **`description`**: 세션 설명
          - **`allowedMinutes`**: 체크인 허용 시간 (분)
          - **`status`**: 세션 상태 (OPEN, CLOSED 등)
          - **`myRole`**: 세션 소유자 | 관리자 | 참가자
          - **`permissions`**: 세션 권한
          """
  )
  @GetMapping("/{sessionId}")
  public ResponseEntity<AttendanceSessionResponse> getSession(
      @PathVariable UUID sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID adminUserId = requireUserId(userDetails);
    AttendanceSessionResponse response = attendanceSessionService.getSessionById(sessionId, adminUserId);
    return ResponseEntity.ok(response);
  }

  /**
   * 모든 세션 목록 조회 - 최신 순으로 정렬 - 공개/비공개 세션 모두 포함
   */
  @Operation(
      summary = "모든 세션 목록 조회",
      description = """
          ## 인증(JWT): **필요없음**
          
          ## 요청 파라미터 : **없음**
          
          ## 반환값 (`List<AttendanceSessionResponse>`)
          - **`sessionId`**: 세션 ID
          - **`title`**: 세션 제목
          - **`description`**: 세션 설명
          - **`allowedMinutes`**: 체크인 허용 시간 (분)
          - **`status`**: 세션 상태 (OPEN, CLOSED 등)
          
          ## 설명
          - 세션 관리자 권한 여부에 따라 반환되는 세션 정보가 다를 수 있음
          """
  )
  @GetMapping
  public ResponseEntity<List<AttendanceSessionResponse>> getAllSessions() {
    List<AttendanceSessionResponse> sessions = attendanceSessionService.getAllSessions();
    return ResponseEntity.ok(sessions);
  }


  /**
   * 현재 활성 세션 목록 조회 - 체크인 가능한 세션들만 조회
   */
  @Operation(
      summary = "활성 세션 목록 조회",
      description = """
          ## 인증(JWT): **필요없음**
          
          ## 요청 파라미터 : **없음**
          
          ## 반환값 (`List<AttendanceSessionResponse>`)
          - **`sessionId`**: 세션 ID
          - **`title`**: 세션 제목
          - **`description`**: 세션 설명
          - **`allowedMinutes`**: 체크인 허용 시간 (분)
          - **`status`**: 세션 상태 (OPEN, CLOSED 등)
          - **`myRole`**: 세션 소유자 | 관리자 | 참가자
          - **`permissions`**: 세션 권한
          """
  )
  @GetMapping("/active")
  public ResponseEntity<List<AttendanceSessionResponse>> getActiveSessions() {
    List<AttendanceSessionResponse> sessions = attendanceSessionService.getActiveSessions();
    return ResponseEntity.ok(sessions);
  }

  /**
   * 세션 정보 수정 (관리자용) - 제목, 시간, 위치, 반경 등 수정 가능 - 코드는 변경 불가
   */
  @Operation(
      summary = "세션 정보 수정",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 ( `AttendanceSessionRequest` )
          - **`title`**: 세션 제목
          - **`description`**: 세션 설명
          - **`allowedMinutes`**: 체크인 허용 시간 (분)
          
          ## 반환값 없음
          """
  )
  @PutMapping("/{sessionId}")
  public ResponseEntity<Void> updateSession(
      @PathVariable UUID sessionId,
      @RequestBody AttendanceSessionRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UUID adminUserId = requireUserId(userDetails);
    attendanceSessionService.updateSession(sessionId, request, adminUserId);
    return ResponseEntity.ok().build();
  }

  /**
   * 세션 종료 (관리자용) - 세션 상태를 CLOSED로 변경 - 체크인 수동 종료
   */
  @Operation(
      summary = "세션 종료",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 ( `sessionId` )
          
          ## 반환값 없음
          """
  )
  @PostMapping("/{sessionId}/close")
  public ResponseEntity<Void> closeSession(@PathVariable UUID sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UUID adminUserId = requireUserId(userDetails);
    attendanceSessionService.closeSession(sessionId, adminUserId);
    return ResponseEntity.ok().build();
  }

  /**
   * 세션 삭제 (관리자용) - 세션 완전 삭제 (출석 기록도 함께 삭제) - 주의: 복구 불가
   */
  @Operation(
      summary = "세션 삭제",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 ( `sessionId` )
          
          ## 반환값 없음
          """
  )
  @DeleteMapping("/{sessionId}")
  public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UUID adminUserId = requireUserId(userDetails);
    attendanceSessionService.deleteSession(sessionId, adminUserId);
    return ResponseEntity.ok().build();
  }
}
