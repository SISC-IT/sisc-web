package org.sejongisc.backend.attendance.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionResponse;
import org.sejongisc.backend.attendance.entity.SessionStatus;
import org.sejongisc.backend.attendance.service.AttendanceSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/attendance/sessions")
@Slf4j
public class AttendanceSessionController {

    private final AttendanceSessionService attendanceSessionService;

    /**
     * 출석 세션 생성 (관리자용)
     * - 6자리 랜덤 코드 자동 생성
     * - GPS 위치 및 반경 설정
     * - 시간 윈도우 설정
     */
    @PostMapping
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<AttendanceSessionResponse> createSession(@Valid @RequestBody AttendanceSessionRequest request) {
        log.info("출석 세션 생성 요청: 제목={}", request.getTitle());

        AttendanceSessionResponse response = attendanceSessionService.createSession(request);

        log.info("출석 세션 생성 완료: 세션ID={}, 코드={}", response.getAttendanceSessionId(), response.getCode());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 세션 상세 조회
     * - 세션 ID로 상세 정보 조회
     * - 남은 시간, 참여자 수 등 포함
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<AttendanceSessionResponse> getSession(@PathVariable UUID sessionId) {
        log.info("춣석 세션 조회: 세션ID={}", sessionId);

        AttendanceSessionResponse response = attendanceSessionService.getSessionById(sessionId);

        return ResponseEntity.ok(response);
    }

    /**
     * 출석 코드로 세션 조회
     * - 학생이 출석 코드 입력 시 사용
     * - 체크인 가능 여부 확인
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<AttendanceSessionResponse> getSessionByCode(@PathVariable String code) {
        log.info("출석 코드로 세션 조회: 코드={}", code);

        AttendanceSessionResponse response = attendanceSessionService.getSessionByCode(code);

        return ResponseEntity.ok(response);
    }

    /**
     * 모든 세션 목록 조회
     * - 최신 순으로 정렬
     * - 공개/비공개 세션 모두 포함
     */
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
    @GetMapping("/active")
    public ResponseEntity<List<AttendanceSessionResponse>> getActiveSessions() {
        log.info("활성 출석 세션 조회");

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getActiveSessions();

        return ResponseEntity.ok(sessions);
    }

    /**
     * 태그별 세션 목록 조회
     * - "금융IT", "동아리 전체" 등 태그로 필터링
     */
    @GetMapping("/tag/{tag}")
    public ResponseEntity<List<AttendanceSessionResponse>> getSessionsByTag(@PathVariable String tag) {
        log.info("태그별 출석 세션 조회: 태그={}", tag);

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getSessionByTag(tag);

        return ResponseEntity.ok(sessions);
    }

    /**
     * 상태별 세션 목록 조회 (관리자용)
     * - UPCOMING/OPEN/CLOSED 상태별 필터링
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<List<AttendanceSessionResponse>> getSessionsByStatus(@PathVariable SessionStatus status) {
        log.info("상태별 출석 세션 조회: 상태={}", status);

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getSessionByStatus(status);

        return ResponseEntity.ok(sessions);
    }

    /**
     * 세션 정보 수정 (관리자용)
     * - 제목, 시간, 위치, 반경 등 수정 가능
     * - 코드는 변경 불가
     */
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
    @PostMapping("/{sessionId}/close")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<Void> closeSession(@PathVariable UUID sessionId) {
        log.info("출석 세션 종료: 세션ID={}", sessionId);

        attendanceSessionService.closeSession(sessionId);

        log.info("출석 세션 종료 오나료: 세션ID={}", sessionId);

        return ResponseEntity.ok().build();
    }

    /**
     * 세션 삭제 (관리자용)
     * - 세션 완전 삭제 (출석 기록도 함께 삭제)
     * - 주의: 복구 불가
     */
    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
        log.info("출석 세션 삭제: 세션ID={}", sessionId);

        attendanceSessionService.deleteSession(sessionId);

        log.info("출석 세션 삭제 완료: 세션ID={}", sessionId);

        return ResponseEntity.noContent().build();
    }
}
