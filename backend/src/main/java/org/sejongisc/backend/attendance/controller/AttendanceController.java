package org.sejongisc.backend.attendance.controller;

import static org.sejongisc.backend.attendance.util.AuthUserUtil.requireUserId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceResponse;
import org.sejongisc.backend.attendance.dto.AttendanceRoundQrTokenRequest;
import org.sejongisc.backend.attendance.dto.AttendanceStatusUpdateRequest;
import org.sejongisc.backend.attendance.service.AttendanceService;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "06. 출석 API", description = "체크인, 출석명단 조회, 출석상태 수정 등 출석 관련 API")
public class AttendanceController {

  private final AttendanceService attendanceService;

  /**
   * ✅ 체크인(세션 멤버)
   * POST /api/attendance/check-in
   * body: { "qrToken": "..." }
   */
  @Operation(
      summary = "체크인",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 바디 ( `AttendanceRoundQrTokenRequest` )
          - **`qrToken`**: QR 토큰
          
          ## 동작 설명
          - qrToken이 유효한지 검증
          - 출석 라운드가 ACTIVE 상태인지 검증
          - 해당 멤버가 출석 세션의 멤버인지 검증
          - 해당 세션의 allowedMinutes 내에 출석체크하면 AttendanceStatus가 PRESENT
          - allowedMinutes가 지난 후에 출석체크하면 LATE
          
          ## 응답
          - **`200 OK`**
          
          ## 에러코드
          - **`QR_TOKEN_MALFORMED`** : QR 토큰 형식이 올바르지 않습니다.
          - **`ROUND_NOT_FOUND`** : 해당 출석 라운드가 존재하지 않습니다.
          - **`ROUND_NOT_ACTIVE`** : 출석 라운드가 진행 중이 아닙니다.
          - **`NOT_SESSION_MEMBER`** : 출석 세션의 멤버가 아닙니다.
          - **`ALREADY_CHECKED_IN`** : 이미 출석 체크되었습니다.
          
          """)
  @PostMapping("/check-in")
  public ResponseEntity<Void> checkIn(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestBody AttendanceRoundQrTokenRequest request
  ) {
    UUID userId = requireUserId(userDetails);
    attendanceService.checkIn(userId, userDetails.getName(), request);
    return ResponseEntity.ok().build();
  }

  /**
   * 라운드별 출석 명단 조회(관리자/OWNER)
   */
  @Operation(
      summary = "라운드 출석 명단 조회",
      description = """
          ## 인증(JWT)
          - **필요**
          
          ## 권한
          - 세션 **MANAGER** 또는 **OWNER**
          
          ## 동작 설명
          - 특정 출석 라운드(`roundId`)에 기록된 모든 출석 데이터를 리스트로 반환
          
          ## 응답 바디 ( `List<AttendanceResponse>` )
          - **유저 정보**: `userId`, `userName`(이름)
          - **세션/라운드 정보**: 세션 제목, 라운드 이름, 장소, 시작 시간 등
          - **출석 상태**: `attendanceStatus` (PENDING, PRESENT, LATE, ABSENT, EXCUSED)
          - **상세 기록**: `checkedAt`(체크인 시각), `note`(비고), `checkInLatitude/Longitude`(위치 정보)
          
          ## 에러 코드
          - **`ROUND_NOT_FOUND`**: 해당 출석 라운드가 존재하지 않습니다.
          - **`NOT_SESSION_ADMIN`**: 세션 관리자 권한이 없습니다.
          
          """)
  @GetMapping("/rounds/{roundId}/records")
  public ResponseEntity<List<AttendanceResponse>> getAttendancesByRound(
      @PathVariable UUID roundId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID adminUserId = requireUserId(userDetails);
    return ResponseEntity.ok(attendanceService.getAttendancesByRound(roundId, adminUserId));
  }

  /**
   * 라운드 내 특정 유저 출석 상태 수정(관리자/OWNER) PUT /api/attendance/rounds/{roundId}/users/{userId}
   */
  @Operation(
      summary = "출석 상태 수정",
      description = """
          
          ## 인증(JWT): **필요**
          
          ## 권한
          - 세션 **MANAGER** 또는 **OWNER** 
          
          ## 경로 파라미터
          - **`roundId`**: 출석 상태를 수정할 라운드 ID (`UUID`)
          - **`userId`**: 출석 상태를 수정할 대상 사용자 ID (`UUID`)
          
          ## 요청 바디 ( `AttendanceStatusUpdateRequest` )
          - **`status`**: 출석 상태 (필수)
            - 허용값 예시: `PRESENT`, `LATE`, `ABSENT`, `EXCUSED`
          - **`reason`**: 상태 수정 사유 (선택)
            - 예: 지각 사유, 공결 사유 등
          
          ## 동작 설명
          - 특정 라운드에서 특정 사용자의 출석 상태를 수정
          - 요청한 사용자가 해당 세션의 관리자/OWNER인지 검증
          - `status` 값과 `reason` 값을 기반으로 출석 상태 반영
          
          ## 응답
          - **200 OK**
          - 수정된 출석 정보 (`AttendanceResponse`)
          """)
  @PutMapping("/rounds/{roundId}/users/{userId}")
  public ResponseEntity<AttendanceResponse> updateAttendanceStatus(
      @PathVariable UUID roundId,
      @PathVariable UUID userId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody AttendanceStatusUpdateRequest request
  ) {
    UUID adminUserId = requireUserId(userDetails);
    AttendanceResponse response =
        attendanceService.updateAttendanceStatusByRound(adminUserId, roundId, userId, request);

    return ResponseEntity.ok(response);
  }

  /**
   * (옵션) 내 출석 이력 조회 GET /api/attendance/me
   */
  @Operation(
      summary = "내 출석 이력 조회",
      description = """
           ## 인증(JWT): **필요**
          
          ## 동작 설명
          - 현재 로그인한 사용자가 참여한 모든 세션 및 라운드의 출석 기록을 최신순으로 조회
          
          ## 응답 바디 ( `List<AttendanceResponse>` )
          - **유저 정보**: `userId`, `userName`(이름)
          - **세션/라운드 정보**: 세션 제목, 라운드 이름, 장소, 시작 시간 등
          - **출석 상태**: `attendanceStatus` (PENDING, PRESENT, LATE, ABSENT, EXCUSED)
          - **상세 기록**: `checkedAt`(체크인 시각), `note`(비고), `checkInLatitude/Longitude`(위치 정보)
          
          ## 에러 코드
          - **`USER_NOT_FOUND`**: 유저 정보를 찾을 수 없습니다.
          
          """)
  @GetMapping("/me")
  public ResponseEntity<List<AttendanceResponse>> getMyAttendances(
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID userId = requireUserId(userDetails);
    return ResponseEntity.ok(attendanceService.getAttendancesByUser(userId));
  }
}
