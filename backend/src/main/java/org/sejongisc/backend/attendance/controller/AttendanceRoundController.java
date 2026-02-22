package org.sejongisc.backend.attendance.controller;

import static org.sejongisc.backend.attendance.util.AuthUserUtil.requireUserId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceRoundQrTokenResponse;
import org.sejongisc.backend.attendance.dto.AttendanceRoundRequest;
import org.sejongisc.backend.attendance.dto.AttendanceRoundResponse;
import org.sejongisc.backend.attendance.service.AttendanceRoundService;
import org.sejongisc.backend.attendance.service.QrTokenStreamService;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "출석 라운드 API",
    description = "출석 라운드(주차별 회차) 생성, 조회, 수정, 삭제 및 출석 체크인 관련 API"
)
public class AttendanceRoundController {

  private final AttendanceRoundService attendanceRoundService;
  private final QrTokenStreamService qrTokenStreamService;

  /**
   * 라운드 생성 (관리자/OWNER) POST /api/attendance/sessions/{sessionId}/rounds
   */
  @Operation(summary = "라운드 생성",
      description = """
        
        ## 인증(JWT): **필요**
        
        
        ## 권한
        - **세션 관리자 / OWNER**
        
        ## 경로 파라미터
        - **`sessionId`**: 라운드를 생성할 출석 세션 ID (`UUID`)
        
        ## 요청 바디 ( `AttendanceRoundRequest` )
        - **`roundDate`**: 라운드 날짜 (`yyyy-MM-dd`)
        - **`startAt`**: 출석 시작 시간 (`yyyy-MM-dd'T'HH:mm:ss`)
        - **`closeAt`**: 출석 마감 시간 (`yyyy-MM-dd'T'HH:mm:ss`)
          - 선택값 (null 가능)
          - null이면 서버에서 자동 계산하도록 구현할 수 있습니다.
        - **`roundName`**: 라운드 이름 (예: 1주차, OT 출석)
        - **`locationName`**: 출석 위치명 (예: 공학관 301호)
        
        ## 동작 설명
        - 지정한 세션에 새로운 출석 라운드를 생성합니다.
        - 요청한 사용자가 해당 세션의 관리자/OWNER인지 검증합니다.
        """)
  @PostMapping("/sessions/{sessionId}/rounds")
  public ResponseEntity<AttendanceRoundResponse> createRound(
      @PathVariable UUID sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestBody AttendanceRoundRequest request
  ) {
    UUID userId = requireUserId(userDetails);

    AttendanceRoundResponse created = attendanceRoundService.createRound(sessionId, userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * 라운드 조회 (세션 멤버) GET /api/attendance/rounds/{roundId}
   */
  @Operation(summary = "라운드 조회", description = "지정된 라운드 ID로 라운드 정보를 조회합니다. (세션 멤버)")
  @GetMapping("/rounds/{roundId}")
  public ResponseEntity<AttendanceRoundResponse> getRound(
      @PathVariable UUID roundId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID userId = requireUserId(userDetails);

    log.info("라운드 조회: roundId={}", roundId);
    AttendanceRoundResponse response = attendanceRoundService.getRound(roundId, userId);
    return ResponseEntity.ok(response);
  }

  /**
   * 세션 내 라운드 목록 조회 (세션 멤버) GET /api/attendance/sessions/{sessionId}/rounds
   */
  @Operation(summary = "세션의 라운드 목록 조회", description = "지정된 세션에 속한 모든 라운드 목록을 조회합니다. (세션 멤버)")
  @GetMapping("/sessions/{sessionId}/rounds")
  public ResponseEntity<List<AttendanceRoundResponse>> getRoundsBySession(
      @PathVariable UUID sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID userId = requireUserId(userDetails);

    log.info("세션 내 라운드 목록 조회: sessionId={}", sessionId);
    List<AttendanceRoundResponse> response = attendanceRoundService.getRoundsBySession(sessionId, userId);
    return ResponseEntity.ok(response);
  }

  /**
   * QR 토큰 발급 (관리자/OWNER) - 서버가 짧게 유효한 qrToken 발급 - 참가자에게는 토큰만 전달(사진 공유해도 만료되면 무효)
   */
  @Operation(summary = "QR 토큰 발급", description = "짧게 유효한 QR 토큰(qrToken)을 발급합니다. (관리자/OWNER, 라운드 ACTIVE 권장)")
  @GetMapping("/rounds/{roundId}/qr-token")
  public ResponseEntity<AttendanceRoundQrTokenResponse> issueQrToken(
      @PathVariable UUID roundId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID userId = requireUserId(userDetails);

    AttendanceRoundQrTokenResponse response = attendanceRoundService.issueQrToken(roundId, userId);
    return ResponseEntity.ok(response);
  }

  /**
   * QR 토큰 SSE 스트림 (관리자/OWNER) - 폴링 없이 3분마다 PUSH
   * GET /api/attendance/rounds/{roundId}/qr-stream
   */
  @Operation(summary = "QR 토큰 SSE 스트림", description = "폴링 없이 SSE로 3분마다 갱신되는 QR 토큰을 push합니다. (관리자/OWNER, 라운드 ACTIVE)")
  @GetMapping(value = "/rounds/{roundId}/qr-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamQrToken(
      @PathVariable UUID roundId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID userId = requireUserId(userDetails);
    return qrTokenStreamService.subscribe(roundId, userId);
  }

  /**
   * 라운드 삭제 (관리자/OWNER) DELETE /api/attendance/rounds/{roundId}
   */
  @Operation(summary = "라운드 삭제", description = "지정된 라운드를 삭제합니다. (관리자/OWNER)")
  @DeleteMapping("/rounds/{roundId}")
  public ResponseEntity<Void> deleteRound(
      @PathVariable UUID roundId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID userId = requireUserId(userDetails);

    log.info("라운드 삭제: roundId={}", roundId);
    attendanceRoundService.deleteRound(roundId, userId);
    return ResponseEntity.noContent().build();
  }
}
