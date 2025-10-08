package org.sejongisc.backend.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceSessionRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionResponse;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.Location;
import org.sejongisc.backend.attendance.entity.SessionStatus;
import org.sejongisc.backend.attendance.entity.SessionVisibility;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AttendanceSessionService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;

    /**
     * Creates and persists a new attendance session from the provided request.
     *
     * If latitude and longitude are present the session will include a Location with the specified radius.
     * The session is assigned a unique 6-digit code, visibility defaults to PUBLIC when not provided,
     * and status is initialized to UPCOMING.
     *
     * @param request the request containing session properties (title, tag, startsAt, windowSeconds, rewardPoints, optional latitude/longitude/radius, and optional visibility)
     * @return an AttendanceSessionResponse representing the persisted session, including the generated code and computed metadata
     */
    public AttendanceSessionResponse createSession(AttendanceSessionRequest request) {
        log.info("출석 세션 생성 시작: 제목={}", request.getTitle());

        String code = generateUniqueCode();
        Location location = null;

        if (request.getLatitude() != null && request.getLongitude() != null) {
            location = Location.builder()
                    .lat(request.getLatitude())
                    .lng(request.getLongitude())
                    .radiusMeters(request.getRadiusMeters())
                    .build();
        }

        AttendanceSession session = AttendanceSession.builder()
                .title(request.getTitle())
                .tag(request.getTag())
                .startsAt(request.getStartsAt())
                .windowSeconds(request.getWindowSeconds())
                .code(code)
                .rewardPoints(request.getRewardPoints())
                .location(location)
                .visibility(request.getVisibility() != null ? request.getVisibility() : SessionVisibility.PUBLIC)
                .status(SessionStatus.UPCOMING)
                .build();

        session = attendanceSessionRepository.save(session);

        log.info("출석 세션 생성 완료: 세션ID={}, 코드={}", session.getAttendanceSessionId(), code);

        return convertToResponse(session);
    }

    /**
     * Fetches an attendance session by its ID and returns a response including computed fields
     * such as endsAt, remainingSeconds, checkInAvailable, and participantCount.
     *
     * @param sessionId the UUID of the attendance session to retrieve
     * @return an AttendanceSessionResponse containing session details and computed metadata
     * @throws IllegalArgumentException if no session exists with the given ID
     */
    @Transactional(readOnly = true)
    public AttendanceSessionResponse getSessionById(UUID sessionId) {
        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        return convertToResponse(session);
    }

    /**
     * Finds an attendance session by its 6-digit attendance code.
     *
     * Used when a student submits a code; the returned response includes dynamic fields
     * such as remainingSeconds and checkInAvailable reflecting the session's current state.
     *
     * @param code the 6-digit attendance code
     * @return an AttendanceSessionResponse representing the matched session and its computed state
     * @throws IllegalArgumentException if no session exists for the given code
     */
    @Transactional(readOnly = true)
    public AttendanceSessionResponse getSessionByCode(String code) {
        AttendanceSession session = attendanceSessionRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 출석 코드입니다: " + code));

        return convertToResponse(session);
    }

    /**
     * Retrieve all attendance sessions ordered by start time, including both public and private sessions.
     *
     * @return a list of AttendanceSessionResponse objects ordered by startsAt in descending order
     */
    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getAllSessions() {
        List<AttendanceSession> sessions = attendanceSessionRepository.findAllByOrderByStartsAtDesc();

        return sessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve attendance sessions that match the specified tag.
     *
     * @param tag the tag to filter sessions by (for example, "금융IT" or "동아리 전체")
     * @return a list of AttendanceSessionResponse objects for sessions with the specified tag; empty list if none match
     */
    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getSessionsByTag(String tag) {
        List<AttendanceSession> sessions = attendanceSessionRepository.findByTag(tag);

        return sessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve attendance sessions filtered by the given session status.
     *
     * @param status the session status to filter by (e.g., UPCOMING, OPEN, CLOSED)
     * @return a list of AttendanceSessionResponse objects matching the provided status
     */
    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getSessionsByStatus(SessionStatus status) {
        List<AttendanceSession> sessions = attendanceSessionRepository.findByStatus(status);

        return sessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get public attendance sessions visible to students, ordered by start time descending.
     *
     * @return a list of AttendanceSessionResponse for sessions with PUBLIC visibility ordered by startsAt descending
     */
    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getPublicSessions() {
        List<AttendanceSession> sessions = attendanceSessionRepository
                .findByVisibilityOrderByStartsAtDesc(SessionVisibility.PUBLIC);

        return sessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve attendance sessions that are currently within their check-in window.
     *
     * @return a list of AttendanceSessionResponse for sessions whose current time is after their start time and before their end time
     */
    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getActiveSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<AttendanceSession> allSessions = attendanceSessionRepository.findAllByOrderByStartsAtDesc();

        return allSessions.stream()
                .filter(session -> {
                    LocalDateTime endTime = session.getStartsAt().plusSeconds(session.getWindowSeconds());
                    return now.isAfter(session.getStartsAt()) && now.isBefore(endTime);
                })
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update an existing attendance session's mutable fields.
     *
     * Updates title, tag, start time, attendance window, reward points, visibility, and location; the session's 6-digit code is left unchanged.
     *
     * @param sessionId the UUID of the attendance session to update
     * @param request   the new session values (if latitude and longitude are provided a location is set; if they are absent the location is cleared)
     * @return          the updated AttendanceSessionResponse representing the saved session
     * @throws IllegalArgumentException if no session exists with the given sessionId
     */
    public AttendanceSessionResponse updateSession(UUID sessionId, AttendanceSessionRequest request) {
        log.info("출석 세션 수정 시작: 세션ID={}", sessionId);

        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        Location location = null;
        if (request.getLatitude() != null && request.getLongitude() != null) {
            location = Location.builder()
                    .lat(request.getLatitude())
                    .lng(request.getLongitude())
                    .radiusMeters(request.getRadiusMeters())
                    .build();
        }

        session = session.toBuilder()
                .title(request.getTitle())
                .tag(request.getTag())
                .startsAt(request.getStartsAt())
                .windowSeconds(request.getWindowSeconds())
                .rewardPoints(request.getRewardPoints())
                .location(location)
                .visibility(request.getVisibility())
                .build();

        session = attendanceSessionRepository.save(session);

        log.info("출석 세션 수정 완료: 세션ID={}", sessionId);

        return convertToResponse(session);
    }

    /**
     * Permanently deletes an attendance session and its associated attendance records.
     *
     * This operation removes the session entity (and cascaded attendance records) from the database and cannot be undone.
     *
     * @param sessionId the UUID of the attendance session to delete
     * @throws IllegalArgumentException if no session exists with the given `sessionId`
     */
    public void deleteSession(UUID sessionId) {
        log.info("출석 세션 삭제 시작: 세션ID={}", sessionId);

        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        attendanceSessionRepository.delete(session);

        log.info("출석 세션 삭제 완료: 세션ID={}", sessionId);
    }

    /**
     * Activates an attendance session by setting its status to OPEN and persisting the change.
     *
     * This forces check-in availability for the session regardless of its scheduled times.
     *
     * @param sessionId the UUID of the attendance session to activate
     * @throws IllegalArgumentException if no session exists with the given id
     */
    public void activateSession(UUID sessionId) {
        log.info("출석 세션 활성화 시작: 세션ID={}", sessionId);

        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        session = session.toBuilder()
                .status(SessionStatus.OPEN)
                .build();

        attendanceSessionRepository.save(session);

        log.info("출석 세션 활성화 완료: 세션ID={}", sessionId);
    }

    /**
     * Manually closes the attendance session identified by the given ID by setting its status to CLOSED.
     *
     * @param sessionId the UUID of the attendance session to close
     * @throws IllegalArgumentException if no session exists for the provided `sessionId`
     */
    public void closeSession(UUID sessionId) {
        log.info("출석 세션 종료 시작: 세션ID={}", sessionId);

        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        session = session.toBuilder()
                .status(SessionStatus.CLOSED)
                .build();

        attendanceSessionRepository.save(session);

        log.info("출석 세션 종료 완료: 세션ID={}", sessionId);
    }

    /**
     * Generate a unique 6-digit numeric attendance code that does not collide with existing sessions.
     *
     * @return a 6-digit numeric string that is not present in the attendance session repository
     */
    private String generateUniqueCode() {
        String code;
        do {
            code = generateRandomCode();
        } while (attendanceSessionRepository.existsByCode(code));
        return code;
    }

    /**
     * Generate a six-digit numeric code.
     *
     * @return a six-character string consisting of digits '0'–'9', ranging from "000000" to "999999"
     */
    private String generateRandomCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * Convert an AttendanceSession entity into an AttendanceSessionResponse DTO.
     *
     * The response includes static session fields and computed, time-dependent values such as `endsAt`,
     * `remainingSeconds`, `checkInAvailable`, and `participantCount`.
     *
     * @param session the AttendanceSession entity to convert
     * @return an AttendanceSessionResponse populated from the entity and computed fields
     */
    private AttendanceSessionResponse convertToResponse(AttendanceSession session) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = session.getStartsAt().plusSeconds(session.getWindowSeconds());

        Long remainingSeconds = null;
        boolean checkInAvailable = false;

        if (now.isBefore(session.getStartsAt())) {
            remainingSeconds = java.time.Duration.between(now, session.getStartsAt()).getSeconds();
        } else if (now.isBefore(endTime)) {
            remainingSeconds = java.time.Duration.between(now, endTime).getSeconds();
            checkInAvailable = true;
        }

        Long participantCount = attendanceRepository.countByAttendanceSession(session);

        return AttendanceSessionResponse.builder()
                .attendanceSessionId(session.getAttendanceSessionId())
                .title(session.getTitle())
                .tag(session.getTag())
                .startsAt(session.getStartsAt())
                .windowSeconds(session.getWindowSeconds())
                .code(session.getCode())
                .rewardPoints(session.getRewardPoints())
                .latitude(session.getLocation() != null ? session.getLocation().getLat() : null)
                .longitude(session.getLocation() != null ? session.getLocation().getLng() : null)
                .radiusMeters(session.getLocation() != null ? session.getLocation().getRadiusMeters() : null)
                .visibility(session.getVisibility())
                .status(session.getStatus())
                .createdAt(session.getCreatedDate())
                .updatedAt(session.getUpdatedDate())
                .endsAt(endTime)
                .remainingSeconds(remainingSeconds)
                .checkInAvailable(checkInAvailable)
                .participantCount(participantCount.intValue())
                .build();

    }

}