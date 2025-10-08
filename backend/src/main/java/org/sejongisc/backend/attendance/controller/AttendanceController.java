package org.sejongisc.backend.attendance.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceRequest;
import org.sejongisc.backend.attendance.dto.AttendanceResponse;
import org.sejongisc.backend.attendance.service.AttendanceService;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.user.entity.User;
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
     * Records a student's attendance for a session using an attendance code and GPS location.
     *
     * @param sessionId the UUID of the session to check into
     * @param request the attendance submission containing the attendance code and GPS location
     * @param userDetails the authenticated principal for the user performing the check-in
     * @return the created AttendanceResponse describing the recorded attendance and its status
     */
    @PostMapping("/sessions/{sessionId}/check-in")
    public ResponseEntity<AttendanceResponse> checkIn(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AttendanceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("출석 체크인 요청: 사용자={}, 코드={}", userDetails.getName(), request.getCode());

        User user = convertToUser(userDetails);
        AttendanceResponse response = attendanceService.checkIn(sessionId, request, user);

        log.info("출석 체크인 완료: 사용자={}, 상태={}", userDetails.getName(), response.getAttendanceStatus());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieve attendance records for a session for administrators.
     *
     * Returns all attendance entries for the specified session ordered by attendance time.
     *
     * @param sessionId the UUID of the session whose attendance records are requested
     * @return a list of AttendanceResponse objects for the session, ordered by attendance time
     */
    @GetMapping("/sessions/{sessionId}/attendances")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<List<AttendanceResponse>> getAttendancesBySession(@PathVariable UUID sessionId) {
        log.info("세션별 출석 목록 조회: 세션ID={}", sessionId);

        List<AttendanceResponse> attendances = attendanceService.getAttendanceBySession(sessionId);

        return ResponseEntity.ok(attendances);
    }

    /**
     * Retrieve the authenticated user's attendance history.
     *
     * @param userDetails the authenticated user's security principal
     * @return a list of AttendanceResponse representing the user's attendance records ordered from newest to oldest
     */
    @GetMapping("/history")
    public ResponseEntity<List<AttendanceResponse>> getMyAttendances(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("내 출석 기록 조회: 사용자={}", userDetails.getName());

        User user = convertToUser(userDetails);
        List<AttendanceResponse> attendances = attendanceService.getAttendancesByUser(user);

        return ResponseEntity.ok(attendances);
    }

    /**
     * Update a member's attendance status for a session.
     *
     * Allows an administrator to set the attendance status (for example, `PRESENT`, `LATE`, `ABSENT`)
     * and optionally provide a reason for the change.
     *
     * @param status       the new attendance status (e.g., `PRESENT`, `LATE`, `ABSENT`)
     * @param reason       optional reason for the status change; may be null
     * @param userDetails  the authenticated administrator performing the update
     * @return             the updated AttendanceResponse reflecting the new status
     */
    @PostMapping("/sessions/{sessionId}/attendances/{memberId}")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<AttendanceResponse> updateAttendanceStatus(
            @PathVariable UUID sessionId,
            @PathVariable UUID memberId,
            @PathVariable String status,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("출석 상태 수정: 세션ID={}, 멤버ID={}, 새로운상태={}, 관리자={}", sessionId, memberId, status, userDetails.getName());

        User user = convertToUser(userDetails);
        AttendanceResponse response = attendanceService.updateAttendanceStatus(sessionId, memberId, status, reason, user);

        log.info("출석 상태 수정 완료: 세션ID={}, 멤버ID={}, 상태={}", sessionId, memberId, response.getAttendanceStatus());

        return ResponseEntity.ok(response);
    }


    /**
     * Convert a CustomUserDetails security principal into a User domain entity.
     *
     * @param userDetails the authenticated principal containing user attributes
     * @return a User populated with the principal's id, name, email, password hash, phone number, role, and point
     */
    private User convertToUser(CustomUserDetails userDetails) {
        return User.builder()
                .userId(userDetails.getUserId())
                .name(userDetails.getName())
                .email(userDetails.getEmail())
                .passwordHash(userDetails.getPassword())
                .phoneNumber(userDetails.getPhoneNumber())
                .role(userDetails.getRole())
                .point(userDetails.getPoint())
                .build();
    }
}