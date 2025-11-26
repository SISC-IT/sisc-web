package org.sejongisc.backend.attendance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceSessionRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionResponse;
import org.sejongisc.backend.attendance.dto.SessionLocationUpdateRequest;
import org.sejongisc.backend.attendance.service.AttendanceSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/attendance/sessions")
@Slf4j
@Tag(
    name = "출석 세션(Attendance Session) API",
    description = "출석 세션 생성, 조회, 수정, 삭제 및 상태 관리 관련 API"
)
public class AttendanceSessionController {

    private final AttendanceSessionService attendanceSessionService;

    /**
     * 출석 세션 생성 (관리자용)
     * - 6자리 랜덤 코드 자동 생성
     * - GPS 위치 및 반경 설정
     * - 시간 윈도우 설정
     */
    @Operation(
            summary = "출석 세션 생성",
            description = "새로운 출석 세션을 생성합니다. (관리자 전용) " +
                    "6자리 랜덤 코드가 자동 생성되며, GPS 위치 정보, 시간 윈도우, " +
                    "보상 포인트 등을 설정할 수 있습니다."
    )
    @PostMapping
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<AttendanceSessionResponse> createSession(@Valid @RequestBody AttendanceSessionRequest request) {
        log.info("출석 세션 생성 요청: 제목={}", request.getTitle());

        AttendanceSessionResponse response = attendanceSessionService.createSession(request);

        log.info("출석 세션 생성 완료: 세션ID={}", response.getAttendanceSessionId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 세션 상세 조회
     * - 세션 ID로 상세 정보 조회
     * - 남은 시간, 참여자 수 등 포함
     */
    @Operation(
            summary = "세션 상세 조회",
            description = "세션 ID로 특정 세션의 상세 정보를 조회합니다. " +
                    "남은 체크인 시간, 현재 참여자 수, 세션 상태 등의 정보가 포함됩니다."
    )
    @GetMapping("/{sessionId}")
    public ResponseEntity<AttendanceSessionResponse> getSession(@PathVariable UUID sessionId) {
        log.info("출석 세션 조회: 세션ID={}", sessionId);

        AttendanceSessionResponse response = attendanceSessionService.getSessionById(sessionId);

        return ResponseEntity.ok(response);
    }

    /**
     * 모든 세션 목록 조회
     * - 최신 순으로 정렬
     * - 공개/비공개 세션 모두 포함
     */
    @Operation(
            summary = "모든 세션 목록 조회",
            description = "생성된 모든 출석 세션을 최신 순으로 조회합니다. " +
                    "공개/비공개 세션이 모두 포함되며, 관리자는 모든 세션을 볼 수 있습니다."
    )
    @GetMapping
    public ResponseEntity<List<AttendanceSessionResponse>> getAllSessions() {
        log.info("모든 출석 세션 조회");

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getAllSessions();

        return ResponseEntity.ok(sessions);
    }

    /**
     * 공개 세션 목록 조회
     * - 학생들이 볼 수 있는 공개 세션만 조회
     * - 최신 순으로 정렬
     */
    @Operation(
            summary = "공개 세션 목록 조회",
            description = "학생들이 볼 수 있는 공개 세션들을 최신 순으로 조회합니다. " +
                    "비공개 세션은 제외됩니다."
    )
    @GetMapping("/public")
    public ResponseEntity<List<AttendanceSessionResponse>> getPublicSessions() {
        log.info("공개 출석 세션 조회");

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getPublicSessions();

        return ResponseEntity.ok(sessions);
    }

    /**
     * 현재 활성 세션 목록 조회
     * - 체크인 가능한 세션들만 조회
     * - 시작시간 ~ 종료 시간 범위 내
     */
    @Operation(
            summary = "활성 세션 목록 조회",
            description = "현재 체크인이 가능한 활성 세션들을 조회합니다. " +
                    "세션 시작 시간부터 시간 윈도우 종료까지 범위 내인 세션들만 조회됩니다."
    )
    @GetMapping("/active")
    public ResponseEntity<List<AttendanceSessionResponse>> getActiveSessions() {
        log.info("활성 출석 세션 조회");

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getActiveSessions();

        return ResponseEntity.ok(sessions);
    }

    /**
     * 세션 정보 수정 (관리자용)
     * - 제목, 시간, 위치, 반경 등 수정 가능
     * - 코드는 변경 불가
     */
    @Operation(
            summary = "세션 정보 수정",
            description = "세션의 기본 정보를 수정합니다. (관리자 전용) " +
                    "제목, 태그, 시간, GPS 위치, 반경, 포인트 등을 수정할 수 있으며, " +
                    "6자리 코드는 변경할 수 없습니다."
    )
    @PutMapping("/{sessionId}")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<AttendanceSessionResponse> updateSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AttendanceSessionRequest request) {

        log.info("출석 세션 수정: 세션ID={}", sessionId);

        AttendanceSessionResponse response = attendanceSessionService.updateSession(sessionId, request);

        log.info("출석 세션 수정 완료: 세션ID={}", sessionId);

        return ResponseEntity.ok(response);
    }

    /**
     * 세션 활성화 (관리자용)
     * - 세션 상태를 OPEN으로 변경
     * - 체크인 수동 활성화
     */
    @Operation(
            summary = "세션 활성화",
            description = "세션을 수동으로 활성화하여 즉시 체크인을 가능하게 합니다. (관리자 전용) " +
                    "세션 상태가 OPEN으로 변경되어 학생들이 체크인할 수 있습니다."
    )
    @PostMapping("/{sessionId}/activate")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<Void> activateSession(@PathVariable UUID sessionId) {
        log.info("출석 세션 활성화: 세션ID={}", sessionId);

        attendanceSessionService.activateSession(sessionId);

        log.info("출석 세션 활성화 완료: 세션ID={}", sessionId);

        return ResponseEntity.ok().build();
    }

    /**
     * 세션 종료 (관리자용)
     * - 세션 상태를 CLOSED로 변경
     * - 체크인 수동 종료
     */
    @Operation(
            summary = "세션 종료",
            description = "세션을 종료합니다. (관리자 전용) " +
                    "세션 상태가 CLOSED로 변경되어 더 이상 체크인이 불가능합니다."
    )
    @PostMapping("/{sessionId}/close")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<Void> closeSession(@PathVariable UUID sessionId) {
        log.info("출석 세션 종료: 세션ID={}", sessionId);

        attendanceSessionService.closeSession(sessionId);

        log.info("출석 세션 종료 완료: 세션ID={}", sessionId);

        return ResponseEntity.ok().build();
    }

    /**
     * 세션 위치 재설정 (관리자용)
     * - 기존 위치 정보를 새로운 위치로 업데이트
     * - 반경은 기존 값 유지
     */
    @Operation(
            summary = "세션 위치 재설정",
            description = "세션의 위치 정보를 재설정합니다. (관리자 전용) " +
                    "새로운 위도와 경도로 출석 기반 위치 검증 범위를 변경할 수 있습니다."
    )
    @PutMapping("/{sessionId}/location")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<AttendanceSessionResponse> updateSessionLocation(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SessionLocationUpdateRequest request) {
        log.info("세션 위치 재설정: 세션ID={}, 위도={}, 경도={}",
                sessionId, request.getLatitude(), request.getLongitude());

        AttendanceSessionResponse response = attendanceSessionService.updateSessionLocation(sessionId, request);

        log.info("세션 위치 재설정 완료: 세션ID={}", sessionId);

        return ResponseEntity.ok(response);
    }

    /**
     * 세션 삭제 (관리자용)
     * - 세션 완전 삭제 (출석 기록도 함께 삭제)
     * - 주의: 복구 불가
     */
    @Operation(
            summary = "세션 삭제",
            description = "세션을 완전히 삭제합니다. (관리자 전용) " +
                    "⚠️ 주의: 해당 세션의 모든 출석 기록이 함께 삭제되며, 복구가 불가능합니다."
    )
    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
        log.info("출석 세션 삭제: 세션ID={}", sessionId);

        attendanceSessionService.deleteSession(sessionId);

        log.info("출석 세션 삭제 완료: 세션ID={}", sessionId);

        return ResponseEntity.noContent().build();
    }
}
