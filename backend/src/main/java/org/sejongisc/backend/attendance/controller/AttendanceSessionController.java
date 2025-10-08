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
     * Create a new attendance session with configured location and time constraints.
     *
     * The created session will include a system-generated 6-digit attendance code, configured GPS location and radius, and the session time window.
     *
     * @param request the attendance session creation payload containing title, location, radius, start/end times, visibility, tags, and other session settings
     * @return the created AttendanceSessionResponse containing the session's identifier, generated code, and persisted session details
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
     * Retrieve detailed information for an attendance session by its ID.
     *
     * Includes session metadata such as remaining time and participant count.
     *
     * @param sessionId the UUID of the attendance session to retrieve
     * @return an AttendanceSessionResponse containing the session details, including remaining time and participant count
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<AttendanceSessionResponse> getSession(@PathVariable UUID sessionId) {
        log.info("춣석 세션 조회: 세션ID={}", sessionId);

        AttendanceSessionResponse response = attendanceSessionService.getSessionById(sessionId);

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the attendance session associated with the given attendance code and whether it can be checked into.
     *
     * @param code the attendance code provided by a student
     * @return the attendance session details, including whether the session is currently open for check-in
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<AttendanceSessionResponse> getSessionByCode(@PathVariable String code) {
        log.info("출석 코드로 세션 조회: 코드={}", code);

        AttendanceSessionResponse response = attendanceSessionService.getSessionByCode(code);

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve all attendance sessions, including both public and private, sorted by newest first.
     *
     * @return a list of AttendanceSessionResponse objects for all sessions sorted by newest first
     */
    @GetMapping
    public ResponseEntity<List<AttendanceSessionResponse>> getAllSessions() {
        log.info("모든 출석 세션 조회");

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getAllSessions();

        return ResponseEntity.ok(sessions);
    }

    /**
     * Retrieves attendance sessions that are publicly visible to students, ordered by most recent.
     *
     * @return a list of AttendanceSessionResponse representing public sessions ordered by most recent
     */
    @GetMapping("/public")
    public ResponseEntity<List<AttendanceSessionResponse>> getPublicSessions() {
        log.info("공개 출석 세션 조회");

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getPublicSessions();

        return ResponseEntity.ok(sessions);
    }

    /**
     * Retrieve the attendance sessions that are currently active and available for check-in.
     *
     * Sessions returned are those whose current time falls between their configured start and end times
     * and are eligible for student check-in.
     *
     * @return a list of AttendanceSessionResponse objects for active, check-in-eligible sessions
     */
    @GetMapping("/active")
    public ResponseEntity<List<AttendanceSessionResponse>> getActiveSessions() {
        log.info("활성 출석 세션 조회");

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getActiveSessions();

        return ResponseEntity.ok(sessions);
    }

    /**
     * Retrieves attendance sessions filtered by the given tag.
     *
     * @param tag the tag name used to filter sessions (e.g., "FinanceIT", "All Clubs")
     * @return a list of AttendanceSessionResponse objects for sessions that match the tag
     */
    @GetMapping("/tag/{tag}")
    public ResponseEntity<List<AttendanceSessionResponse>> getSessionsByTag(@PathVariable String tag) {
        log.info("태그별 출석 세션 조회: 태그={}", tag);

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getSessionsByTag(tag);

        return ResponseEntity.ok(sessions);
    }

    /**
     * Retrieve attendance sessions filtered by status for administrators.
     *
     * @param status the session status to filter by — one of UPCOMING, OPEN, or CLOSED
     * @return a list of AttendanceSessionResponse objects matching the specified status
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
    public ResponseEntity<List<AttendanceSessionResponse>> getSessionsByStatus(@PathVariable SessionStatus status) {
        log.info("상태별 출석 세션 조회: 상태={}", status);

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getSessionsByStatus(status);

        return ResponseEntity.ok(sessions);
    }

    /**
     * Update an existing attendance session's editable fields.
     *
     * <p>Modifiable fields include title, time, location, and radius; the session code cannot be changed.</p>
     *
     * @param sessionId the UUID of the session to update
     * @param request   request payload containing the new values for editable session fields
     * @return the updated attendance session
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
     * Activate an attendance session and enable manual check-in (administrative action).
     *
     * @param sessionId the UUID of the attendance session to activate
     * @return HTTP 200 OK with an empty body
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
     * Closes an attendance session and terminates any active check-ins.
     *
     * Sets the session's status to CLOSED and ends ongoing manual check-ins for the specified session.
     *
     * @param sessionId the UUID of the attendance session to close
     * @return a ResponseEntity with HTTP 200 OK and an empty body
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
     * Permanently deletes an attendance session and all associated attendance records.
     *
     * This operation is irreversible and intended for administrator use.
     *
     * @param sessionId UUID of the attendance session to delete
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