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
    name = "06. 출석 라운드 API",
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
        - **세션 OWNER**
        
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
        - 특정 출석 세션 내에 새로운 출석 회차(Round) 생성
        - `closeAt`을 null 값으로 요청 시 기본값 : 시작 3시간 후로 설정
        - 요청한 사용자가 해당 세션의 OWNER인지 검증
        - AttendanceRoundStatus는 기본적으로 `UPCOMING`으로 생성
        - 시작 시간이 되면 `ACTIVE`로 변경 (최대 1분 정도 소요될 수 있음)
        
        ## 에러 코드
        - **`SESSION_NOT_FOUND`**: 해당 출석 세션이 존재하지 않습니다.
        - **`NOT_SESSION_OWNER`**: 세션 소유자 권한이 없습니다.
        - **`ROUND_DATE_REQUIRED`**: 출석 라운드 날짜가 필요합니다.
        - **`START_AT_REQUIRED`**: 출석 라운드 시작 시간이 필요합니다.
        - **`ROUND_NAME_REQUIRED`**: 출석 라운드 이름이 필요합니다.
        - **`END_AT_MUST_BE_AFTER_START_AT`**: 종료 시간은 시작 시간 이후여야 합니다.
        
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
   * 라운드 삭제 (관리자/OWNER) DELETE /api/attendance/rounds/{roundId}
   */
  @Operation(
      summary = "라운드 삭제",
      description = """
      ## 인증(JWT)
      - **필요**
      
      ## 권한
      - **세션 OWNER**
      
      ## 경로 파라미터
      - **`roundId`**: 삭제할 라운드 ID (`UUID`)
      
      ## 요청 바디
      - **없음**
      
      ## 동작 설명
      - 특정 출석 회차(Round)를 삭제
      - 요청 유저가 해당 세션의 OWNER 권한을 가졌는지 검증
      - **중요**: 해당 라운드에 등록된 모든 출석 기록(Attendance)도 함께 삭제됨
        - 특정 라운드의 모든 참가자 출석 현황 데이터 삭제
        - 삭제된 기록은 복구 불가능
      
      ## 에러 코드
      - **`ROUND_NOT_FOUND`**: 해당 출석 라운드가 존재하지 않습니다.
      - **`NOT_SESSION_OWNER`**: 세션 소유자 권한이 없습니다.
      
      """)  @DeleteMapping("/rounds/{roundId}")
  public ResponseEntity<Void> deleteRound(
      @PathVariable UUID roundId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID userId = requireUserId(userDetails);
    attendanceRoundService.deleteRound(roundId, userId);
    return ResponseEntity.noContent().build();
  }

  /**
   * 라운드 조회 (세션 멤버) GET /api/attendance/rounds/{roundId}
   */
  @Operation(
      summary = "라운드 상세 조회",
      description = """
        
        ## 인증(JWT): **필요**
        
        ## 권한
        - **세션 MEMBER/MANAGER/OWNER**
        
        ## 동작 설명
        - 특정 라운드 ID(`roundId`)를 통해 회차의 상세 정보 조회
        - 해당 세션에 참여하지 않은 멤버는 조회할 수 없음
        - `SessionUserController`에서 세션에 멤버 추가해야함
        
        ## 에러 코드
        - **`ROUND_NOT_FOUND`**: 해당 출석 라운드가 존재하지 않습니다.
        - **`NOT_SESSION_MEMBER`**: 출석 세션의 멤버가 아닙니다.
        
        """)
  @GetMapping("/rounds/{roundId}")
  public ResponseEntity<AttendanceRoundResponse> getRound(
      @PathVariable UUID roundId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID userId = requireUserId(userDetails);
    AttendanceRoundResponse response = attendanceRoundService.getRound(roundId, userId);
    return ResponseEntity.ok(response);
  }

  /**
   * 세션 내 라운드 목록 조회 (세션 멤버) GET /api/attendance/sessions/{sessionId}/rounds
   */
  @Operation(
      summary = "세션의 라운드 목록 조회",
      description = """
        
        ## 인증(JWT): **필요**
        
        ## 권한
        - **세션 MEMBER/MANAGER/OWNER**
        
        ## 동작 설명
        - 특정 출석 세션(`sessionId`)에 포함된 모든 라운드(회차) 목록 반환
        - 결과는 라운드 날짜(`roundDate`) 기준 **오름차순**으로 정렬되어 반환
        - 세션에 참여하지 않은 사용자는 목록을 조회할 수 없음
        
        ## 에러 코드
        - **`SESSION_NOT_FOUND`**: 해당 출석 세션이 존재하지 않습니다.
        - **`UNAUTHENTICATED`**: 인증되지 않은 사용자입니다.
        - **`NOT_SESSION_MEMBER`**: 출석 세션의 멤버가 아닙니다.
        
        
        """)  @GetMapping("/sessions/{sessionId}/rounds")
  public ResponseEntity<List<AttendanceRoundResponse>> getRoundsBySession(
      @PathVariable UUID sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID userId = requireUserId(userDetails);
    List<AttendanceRoundResponse> response = attendanceRoundService.getRoundsBySession(sessionId, userId);
    return ResponseEntity.ok(response);
  }

  /**
   * QR 토큰 발급 (관리자/OWNER) - 서버가 짧게 유효한 qrToken 발급 - 참가자에게는 토큰만 전달(사진 공유해도 만료되면 무효)
   */
  @Operation(
      summary = "QR 토큰 발급",
      description = """
        ## 인증(JWT)
        - **필요**
        
        ## 권한
        - **세션 MANAGER** 또는 **OWNER**
        
        ## 경로 파라미터
        - **`roundId`**: QR 토큰을 생성할 라운드 ID (`UUID`)
        
        ## 동작 설명
        - 특정 라운드 출석용 단기 유효 QR 토큰 생성
        - 토큰 유효 시간: 약 3분
        - 만료된 토큰이나 유효하지 않은 비밀키로 생성된 토큰은 출석 체크 불가
        - 해당 라운드의 상태가 **ACTIVE**인 경우에만 발급 가능
        
        ## 에러 코드
        - **`ROUND_NOT_FOUND`**: 해당 출석 라운드가 존재하지 않습니다.
        - **`ROUND_NOT_ACTIVE`**: 출석 라운드가 진행 중이 아닙니다.
        - **`NOT_SESSION_ADMIN`**: 세션 관리자 권한이 없습니다.
        
        """)  @GetMapping("/rounds/{roundId}/qr-token")
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
  @Operation(
      summary = "QR 토큰 SSE 스트림",
      description = """
      ## 인증(JWT)
      - **필요**
      
      ## 권한
      - **세션 MANAGER** 또는 **OWNER**
      
      ## 경로 파라미터
      - **`roundId`**: 실시간 QR 토큰을 구독할 라운드 ID (`UUID`)
      
      ## 요청 바디
      - **없음** (Content-Type: `text/event-stream`)
      
      ## 동작 설명
      - 폴링(Polling) 없이 서버에서 클라이언트로 갱신된 QR 토큰을 자동 Push
      - 구독 즉시 현재 시점의 유효한 QR 토큰 최초 1회 발송
      - 이후 윈도우(약 3분) 경계마다 갱신된 토큰을 `qrToken` 이벤트명으로 발송
      - 연결 유지를 위해 15초마다 `ping` 이벤트 발송 (Idle Timeout 방지)
      - 라운드 상태가 **ACTIVE**가 아니거나 종료될 경우 스트림 자동 종료
      - 브라우저 종료나 페이지 이탈 시 서버 리소스 자동 정리
      
      ## 에러 코드
      - **`ROUND_NOT_FOUND`**: 해당 출석 라운드가 존재하지 않습니다.
      - **`ROUND_NOT_ACTIVE`**: 출석 라운드가 진행 중이 아닙니다.
      - **`NOT_SESSION_ADMIN`**: 세션 관리자 권한이 없습니다.
      
      """)
  @GetMapping(value = "/rounds/{roundId}/qr-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamQrToken(
      @PathVariable UUID roundId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID userId = requireUserId(userDetails);
    return qrTokenStreamService.subscribe(roundId, userId);
  }
}
