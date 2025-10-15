package org.sejongisc.backend.attendance.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceRequest;
import org.sejongisc.backend.attendance.dto.AttendanceResponse;
import org.sejongisc.backend.attendance.service.AttendanceService;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/attendance")
@Slf4j
public class AttendanceController {

    private final AttendanceService attendanceService;

    /**
     * 학생 출석 체크인
     * - 출석 코드와 GPS 위치를 이요한 춣석 처리
     * - 위치 범위, 시간 위도우 검증 포함
     * - 중복 출석 방지
     */
    @PostMapping("/sessions/{sessionId}/check-in")
    public ResponseEntity<AttendanceResponse> checkIn(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AttendanceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("출석 체크인 요청: 사용자={}, 코드={}", userDetails.getName(), request.getCode());

        AttendanceResponse response = attendanceService.checkIn(sessionId, request, userDetails.getUserId());

        log.info("출석 체크인 완료: 사용자={}, 상태={}", userDetails.getName(), response.getAttendanceStatus());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 세션별 출석 목록 조회(관리자용)
     * - 특정 세션의 모든 출석 기록 조회
     * - 출석 시간 순으로 정렬
     */
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
