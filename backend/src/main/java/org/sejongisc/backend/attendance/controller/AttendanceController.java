package org.sejongisc.backend.attendance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceResponse;
import org.sejongisc.backend.attendance.service.AttendanceService;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/attendance")
@Slf4j
@Tag(
    name = "출석(Attendance) API",
    description = "학생 출석 체크인 및 관리자 출석 현황 조회 관련 API"
)
public class AttendanceController {

    private final AttendanceService attendanceService;

    /**
     * 세션별 출석 목록 조회(관리자용)
     * - 특정 세션의 모든 출석 기록 조회
     * - 출석 시간 순으로 정렬
     */
    @Operation(
            summary = "세션별 출석 목록 조회",
            description = "특정 세션에 참가한 모든 학생의 출석 기록을 조회합니다. (관리자 전용) " +
                    "출석 시간 순으로 정렬되며, 각 학생의 상태, 체크인 시간, 포인트 등이 포함됩니다."
    )
    @GetMapping("/sessions/{sessionId}/attendances")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<List<AttendanceResponse>> getAttendancesBySession(@PathVariable UUID sessionId) {
        log.info("세션별 출석 목록 조회: 세션ID={}", sessionId);

        List<AttendanceResponse> attendances = attendanceService.getAttendancesBySession(sessionId);

        return ResponseEntity.ok(attendances);
    }

    /**
     * 내 출석 기록 조회
     * - 로그인한 사용자의 모든 출석 기록 조회
     * - 최신 순으로 정렬
     */
    @Operation(
            summary = "내 출석 기록 조회",
            description = "로그인한 사용자의 모든 출석 기록을 최신 순으로 조회합니다. " +
                    "각 출석 기록에는 세션 정보, 출석 상태, 체크인 시간, 획득 포인트 등이 포함됩니다."
    )
    @GetMapping("/history")
    public ResponseEntity<List<AttendanceResponse>> getMyAttendances(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("내 출석 기록 조회: 사용자={}", userDetails.getName());

        List<AttendanceResponse> attendances = attendanceService.getAttendancesByUser(userDetails.getUserId());

        return ResponseEntity.ok(attendances);
    }

    /**
     * 출석 상태 수정(관리자용)
     * - PRESENT/LATE/ABSENT 등으로 상태 변경
     * - 수정 사유 기록 가능
     */
    @Operation(
            summary = "출석 상태 수정",
            description = "특정 학생의 출석 상태를 변경합니다. (관리자 전용) " +
                    "PRESENT(출석), LATE(지각), ABSENT(결석), EXCUSED(사유결석) 등의 상태로 변경 가능하며, " +
                    "변경 사유를 함께 기록할 수 있습니다."
    )
    @PostMapping("/sessions/{sessionId}/attendances/{memberId}")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<AttendanceResponse> updateAttendanceStatus(
            @PathVariable UUID sessionId,
            @PathVariable UUID memberId,
            @RequestParam String status,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("출석 상태 수정: 세션ID={}, 멤버ID={}, 새로운상태={}, 관리자={}", sessionId, memberId, status, userDetails.getName());

        AttendanceResponse response = attendanceService.updateAttendanceStatus(sessionId, memberId, status, reason, userDetails.getUserId());

        log.info("출석 상태 수정 완료: 세션ID={}, 멤버ID={}, 상태={}", sessionId, memberId, response.getAttendanceStatus());

        return ResponseEntity.ok(response);
    }
}
