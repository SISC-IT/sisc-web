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
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    name = "06. 출석 세션 API",
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
          
          ## 인증(JWT)
          - **필요**
          
          ## 요청 바디 ( `AttendanceSessionRequest` )
          - **`title`**: 세션 제목 (예: 2024 ISC 정기 세션)
          - **`description`**: 세션 상세 설명
          - **`allowedMinutes`**: 지각 처리 전 체크인 허용 시간 (분 단위)
          
          ## 동작 설명
          - 새로운 출석 세션(Session) 엔티티 생성
          - 세션 상태(`SessionStatus`)는 기본적으로 **OPEN**으로 설정
          - 세션 생성자를 해당 세션의 **OWNER** 권한으로 자동 등록 (`SessionUser`)
          
          ## 에러 코드
          - **`USER_NOT_FOUND`**: 유저를 찾을 수 없습니다.
          
          """)
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
         
          ## 경로 파라미터
          - **`sessionId`**: 조회할 세션 ID (`UUID`)
          
          ## 동작 설명
          - 특정 세션의 정보와 요청한 유저의 권한 정보를 함께 조회
          - 유저가 세션 멤버가 아닐 경우 역할(`myRole`)은 `null`로 반환
          
          ## 에러 코드
          - **`SESSION_NOT_FOUND`**: 해당 출석 세션이 존재하지 않습니다.
          - **`USER_NOT_FOUND`**: 유저를 찾을 수 없습니다.
          
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
          
          ## 동작 설명
          - 모든 출석 세션 목록을 반환
          - 특정 유저의 역할(Role) 정보 없이 세션의 정보만 리스트 형식으로 전달
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
          
          ## 동작 설명
          - 현재 체크인이 가능한 상태(`SessionStatus.OPEN`)인 세션들만 필터링하여 반환
          - 종료된 세션(`CLOSED`)은 목록에서 제외됨
          
          """
  )
  @GetMapping("/active")
  public ResponseEntity<List<AttendanceSessionResponse>> getActiveSessions() {
    List<AttendanceSessionResponse> sessions = attendanceSessionService.getActiveSessions();
    return ResponseEntity.ok(sessions);
  }

  /**
   * 세션 정보 수정 (관리자용) - 제목, 설명, 허용 시간 수정 가능
   */
  @Operation(
      summary = "세션 정보 수정",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 ( `AttendanceSessionRequest` )
          - **`title`**: 세션 제목
          - **`description`**: 세션 설명
          - **`allowedMinutes`**: 체크인 허용 시간 (분)
          
          ## 반환값 : 없음
          
          ## 에러 코드
          - **`SESSION_NOT_FOUND`**: 해당 출석 세션이 존재하지 않습니다.
          
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
      ## 인증(JWT)
      - **필요**
      
      ## 권한
      - **세션 MANAGER** 또는 **OWNER**
      
      ## 경로 파라미터
      - **`sessionId`**: 종료할 세션 ID (`UUID`)
      
      ## 동작 설명
      - 세션의 상태를 **CLOSED**로 변경하여 수동으로 종료 처리
      - 종료된 세션은 더 이상 체크인이 불가능하도록 제한됨
      
      ## 에러 코드
      - **`SESSION_NOT_FOUND`**: 해당 출석 세션이 존재하지 않습니다.
      - **`NOT_SESSION_ADMIN`**: 세션 관리자 권한이 없습니다.

      """)
  @PostMapping("/{sessionId}/close")
  public ResponseEntity<Void> closeSession(
      @PathVariable UUID sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
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
      ## 인증(JWT)
      - **필요**
      
      ## 권한
      - **세션 MANAGER** 또는 **OWNER**
      
      ## 경로 파라미터
      - **`sessionId`**: 삭제할 세션 ID (`UUID`)
      
      ## 동작 설명
      - 세션 정보를 삭제
      - **주의**: 해당 세션에 귀속된 모든 라운드 및 출석 기록이 삭제되며 복구가 불가능함
      
      ## 에러 코드
      - **`SESSION_NOT_FOUND`**: 해당 출석 세션이 존재하지 않습니다.
      - **`NOT_SESSION_ADMIN`**: 세션 관리자 권한이 없습니다.
      
      """)
  @DeleteMapping("/{sessionId}")
  public ResponseEntity<Void> deleteSession(
      @PathVariable UUID sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID adminUserId = requireUserId(userDetails);
    attendanceSessionService.deleteSession(sessionId, adminUserId);
    return ResponseEntity.ok().build();
  }
}
