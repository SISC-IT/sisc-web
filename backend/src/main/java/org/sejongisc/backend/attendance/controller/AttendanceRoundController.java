package org.sejongisc.backend.attendance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceCheckInRequest;
import org.sejongisc.backend.attendance.dto.AttendanceCheckInResponse;
import org.sejongisc.backend.attendance.dto.AttendanceRoundRequest;
import org.sejongisc.backend.attendance.dto.AttendanceRoundResponse;
import org.sejongisc.backend.attendance.service.AttendanceRoundService;
import org.sejongisc.backend.attendance.service.AttendanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "출석 라운드(Attendance Round) API",
        description = "출석 라운드(주차별 회차) 생성, 조회, 수정, 삭제 및 출석 체크인 관련 API"
)
public class AttendanceRoundController {

    private final AttendanceRoundService attendanceRoundService;
    private final AttendanceService attendanceService;

    /**
     * 라운드 생성
     * POST /api/attendance/sessions/{sessionId}/rounds
     */
    @Operation(
            summary = "라운드 생성",
            description = "세션에 새로운 출석 라운드를 생성합니다. " +
                    "라운드 날짜, 시작 시간, 출석 가능 시간을 설정할 수 있습니다."
    )
    @PostMapping("/sessions/{sessionId}/rounds")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<AttendanceRoundResponse> createRound(
            @PathVariable UUID sessionId,
            @RequestBody AttendanceRoundRequest request) {
        log.info("📋 라운드 생성 요청 도착:");
        log.info("  - sessionId: {}", sessionId);
        log.info("  - roundDate: {} (타입: {})", request.getRoundDate(), request.getRoundDate() != null ? request.getRoundDate().getClass().getSimpleName() : "null");
        log.info("  - startTime: {} (타입: {})", request.getStartTime(), request.getStartTime() != null ? request.getStartTime().getClass().getSimpleName() : "null");
        log.info("  - allowedMinutes: {}", request.getAllowedMinutes());

        if (request.getStartTime() != null) {
            log.info("  - startTime 상세: 시간={}, 분={}, 초={}",
                    request.getStartTime().getHour(),
                    request.getStartTime().getMinute(),
                    request.getStartTime().getSecond());
        }

        AttendanceRoundResponse response = attendanceRoundService.createRound(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 라운드 조회 (개별)
     * GET /api/attendance/rounds/{roundId}
     */
    @Operation(
            summary = "라운드 조회",
            description = "지정된 라운드 ID로 라운드 정보를 조회합니다. " +
                    "라운드의 상태, 날짜, 시간, 참석 현황 등의 정보를 반환합니다."
    )
    @GetMapping("/rounds/{roundId}")
    public ResponseEntity<AttendanceRoundResponse> getRound(@PathVariable UUID roundId) {
        log.info("라운드 조회: roundId={}", roundId);
        AttendanceRoundResponse response = attendanceRoundService.getRound(roundId);
        return ResponseEntity.ok(response);
    }

    /**
     * 세션 내 라운드 목록 조회
     * GET /api/attendance/sessions/{sessionId}/rounds
     */
    @Operation(
            summary = "세션의 라운드 목록 조회",
            description = "지정된 세션에 속한 모든 라운드 목록을 조회합니다. " +
                    "각 라운드의 상태, 시간, 참석 현황을 포함합니다."
    )
    @GetMapping("/sessions/{sessionId}/rounds")
    public ResponseEntity<List<AttendanceRoundResponse>> getRoundsBySession(
            @PathVariable UUID sessionId) {
        log.info("세션 내 라운드 목록 조회: sessionId={}", sessionId);
        List<AttendanceRoundResponse> response = attendanceRoundService.getRoundsBySession(sessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * 라운드 정보 수정
     * PUT /api/attendance/rounds/{roundId}
     */
    @Operation(
            summary = "라운드 정보 수정",
            description = "지정된 라운드의 정보를 수정합니다. " +
                    "라운드 날짜, 시작 시간, 출석 가능 시간 등을 변경할 수 있습니다."
    )
    @PutMapping("/rounds/{roundId}")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<AttendanceRoundResponse> updateRound(
            @PathVariable UUID roundId,
            @RequestBody AttendanceRoundRequest request) {
        log.info("라운드 수정: roundId={}", roundId);
        AttendanceRoundResponse response = attendanceRoundService.updateRound(roundId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 라운드 삭제
     * DELETE /api/attendance/rounds/{roundId}
     */
    @Operation(
            summary = "라운드 삭제",
            description = "지정된 라운드를 삭제합니다. " +
                    "라운드와 관련된 모든 출석 기록도 함께 삭제됩니다."
    )
    @DeleteMapping("/rounds/{roundId}")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<Void> deleteRound(@PathVariable UUID roundId) {
        log.info("라운드 삭제: roundId={}", roundId);
        attendanceRoundService.deleteRound(roundId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 라운드 기반 출석 체크인
     * POST /api/attendance/rounds/check-in
     */
    @Operation(
            summary = "라운드 출석 체크인",
            description = "라운드에 출석 체크인을 기록합니다. " +
                    "라운드 ID와 위치 정보(위도, 경도)를 전송하면 출석 여부를 판단합니다. " +
                    "인증되지 않은 사용자는 이름을 입력하여 익명으로 출석할 수 있습니다."
    )
    @PostMapping("/rounds/check-in")
    public ResponseEntity<AttendanceCheckInResponse> checkInByRound(
            @Valid @RequestBody AttendanceCheckInRequest request,
            Authentication authentication) {
        UUID userId = null;

        // 인증된 경우 사용자 ID 추출, 미인증인 경우 임시 ID 생성
        if (authentication != null && authentication.isAuthenticated()) {
            try {
                userId = UUID.fromString(authentication.getName());
                log.info("라운드 출석 체크인 요청 (인증됨): roundId={}, userId={}", request.getRoundId(), userId);
            } catch (Exception e) {
                log.warn("사용자 ID 파싱 실패, 임시 ID 사용: {}", e.getMessage());
                userId = UUID.randomUUID();
            }
        } else {
            // 미인증 사용자: 임시 ID 사용
            userId = UUID.randomUUID();
            log.info("라운드 출석 체크인 요청 (미인증): roundId={}, 임시userId={}", request.getRoundId(), userId);
        }

        AttendanceCheckInResponse response = attendanceService.checkInByRound(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 특정 날짜의 라운드 조회
     * GET /api/attendance/sessions/{sessionId}/rounds/by-date
     */
    @Operation(
            summary = "특정 날짜의 라운드 조회",
            description = "지정된 세션과 날짜로 라운드를 조회합니다. " +
                    "특정 날짜에만 진행되는 라운드를 찾을 때 사용합니다."
    )
    @GetMapping("/sessions/{sessionId}/rounds/by-date")
    public ResponseEntity<AttendanceRoundResponse> getRoundByDate(
            @PathVariable UUID sessionId,
            @RequestParam LocalDate date) {
        log.info("날짜별 라운드 조회: sessionId={}, date={}", sessionId, date);
        AttendanceRoundResponse response = attendanceRoundService.getRoundByDate(sessionId, date);
        return ResponseEntity.ok(response);
    }

    /**
     * 라운드별 출석 명단 조회
     * GET /api/attendance/rounds/{roundId}/attendances
     */
    @Operation(
            summary = "라운드별 출석 명단 조회",
            description = "지정된 라운드의 모든 출석 기록을 조회합니다. " +
                    "참석자, 지각자, 결석자 등의 출석 상태별 명단을 반환합니다."
    )
    @GetMapping("/rounds/{roundId}/attendances")
    public ResponseEntity<?> getAttendancesByRound(
            @PathVariable UUID roundId) {
        log.info("라운드별 출석 명단 조회: roundId={}", roundId);
        // 라운드 조회 및 해당 라운드의 모든 출석 기록 반환
        try {
            var round = attendanceService.getAttendancesByRound(roundId);
            return ResponseEntity.ok(round);
        } catch (Exception e) {
            log.error("라운드별 출석 명단 조회 실패: {}", e.getMessage());
            return ResponseEntity.status(400).body(new java.util.HashMap<String, String>() {{
                put("error", "라운드를 찾을 수 없습니다");
            }});
        }
    }
}
