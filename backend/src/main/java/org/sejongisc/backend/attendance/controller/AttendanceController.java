package org.sejongisc.backend.attendance.controller;

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
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "출석 API", description = "체크인, 출석명단 조회, 출석상태 수정 등 출석 관련 API")
public class AttendanceController {

  private final AttendanceService attendanceService;

  /**
   * ✅ 체크인(세션 멤버)
   * POST /api/attendance/check-in
   * body: { "qrToken": "..." }
   */
  @Operation(summary = "체크인", description = "qrToken으로 출석 체크인합니다. (세션 멤버)")
  @PostMapping("/check-in")
  public ResponseEntity<Void> checkIn(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestBody AttendanceRoundQrTokenRequest request
  ) {
    UUID userId = requireUserId(userDetails);
    attendanceService.checkIn(userId, request);
    return ResponseEntity.ok().build();
  }

  /**
   * 라운드별 출석 명단 조회(관리자/OWNER)
   */
  @Operation(summary = "라운드 출석 명단 조회", description = "특정 라운드의 출석 기록을 조회합니다. (관리자/OWNER)")
  @GetMapping("/rounds/{roundId}")
  public ResponseEntity<List<AttendanceResponse>> getAttendancesByRound(
      @PathVariable UUID roundId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID adminUserId = requireUserId(userDetails);
    return ResponseEntity.ok(attendanceService.getAttendancesByRound(roundId, adminUserId));
  }

  /**
   *  라운드 내 특정 유저 출석 상태 수정(관리자/OWNER)
   * PUT /api/attendance/rounds/{roundId}/users/{userId}
   */
  @Operation(summary = "출석 상태 수정", description = "특정 라운드에서 특정 유저의 출석 상태를 수정합니다. (관리자/OWNER)")
  @PutMapping("/rounds/{roundId}/users/{userId}")
  public ResponseEntity<AttendanceResponse> updateAttendanceStatus(
      @PathVariable UUID roundId,
      @PathVariable UUID userId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody AttendanceStatusUpdateRequest request
  ) {
    UUID adminUserId = requireUserId(userDetails);

    // status가 enum이든 string이든 안전하게 문자열로 변환
    String statusStr = String.valueOf(request.getStatus());
    String reason = request.getReason();

    AttendanceResponse response =
        attendanceService.updateAttendanceStatusByRound(adminUserId, roundId, userId, statusStr, reason);

    return ResponseEntity.ok(response);
  }

  /**
   * (옵션) 내 출석 이력 조회
   * GET /api/attendance/me
   */
  @Operation(summary = "내 출석 이력 조회", description = "로그인한 사용자의 출석 이력을 조회합니다.")
  @GetMapping("/me")
  public ResponseEntity<List<AttendanceResponse>> getMyAttendances(
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    UUID userId = requireUserId(userDetails);
    return ResponseEntity.ok(attendanceService.getAttendancesByUser(userId));
  }



  // ------- helper -------
  private UUID requireUserId(CustomUserDetails userDetails) {
    if (userDetails == null) throw new IllegalStateException("UNAUTHENTICATED");
    return userDetails.getUserId();
  }
}
