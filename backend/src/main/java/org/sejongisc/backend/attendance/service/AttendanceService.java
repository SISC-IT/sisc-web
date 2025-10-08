package org.sejongisc.backend.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceRequest;
import org.sejongisc.backend.attendance.dto.AttendanceResponse;
import org.sejongisc.backend.attendance.entity.Attendance;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.AttendanceStatus;
import org.sejongisc.backend.attendance.entity.Location;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;


    /**
     * Process a user's attendance check-in for the specified session.
     *
     * Validates session existence and code, prevents duplicate check-ins, verifies the user's GPS location is within the session's allowed range, enforces the session time window, determines PRESENT or LATE status, persists the attendance, and returns the saved attendance as a response DTO.
     *
     * @param sessionId the identifier of the attendance session
     * @param request   the check-in request containing the session code, latitude, longitude, optional note, and device information
     * @param user      the user performing the check-in
     * @return          an AttendanceResponse representing the persisted attendance record
     * @throws IllegalArgumentException if the session does not exist, the provided code does not match the session code, or the user's location is outside the allowed range
     * @throws IllegalStateException    if the user has already checked in for the session, the check-in occurs before the session start, or the check-in occurs after the session's allowed window
     */
    public AttendanceResponse checkIn(UUID sessionId, AttendanceRequest request, User user) {
        log.info("출석 체크인 시작: 사용자={}, 세션ID={}, 코드={}", user.getName(), sessionId, request.getCode());

        // 세션ID로 세션 조회
        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        // 세션의 코드와 요청된 코드가 일치하는지 검증
        if (!session.getCode().equals(request.getCode())) {
            throw new IllegalArgumentException("세션 코드가 일치하지 않습니다");
        }

        if (attendanceRepository.existsByAttendanceSessionAndUser(session, user)) {
            throw new IllegalStateException("이미 출석 체크인한 세션입니다");
        }

        Location userLocation = Location.builder()
                .lat(request.getLatitude())
                .lng(request.getLongitude())
                .build();

        if (!session.getLocation().isWithRange(userLocation)) {
            throw new IllegalArgumentException("출석 허용 범위를 벗어났습니다");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(session.getStartsAt())) {
            throw new IllegalStateException("아직 출석 시간이 아닙니다");
        }

        LocalDateTime endTime = session.getStartsAt().plusSeconds(session.getWindowSeconds());
        if (now.isAfter(endTime)) {
            throw new IllegalStateException("출석 시간이 종료되었습니다");
        }

        AttendanceStatus status = now.isAfter(session.getStartsAt()) ?
                AttendanceStatus.LATE : AttendanceStatus.PRESENT;

        Attendance attendance = Attendance.builder()
                .user(user)
                .attendanceSession(session)
                .attendanceStatus(status)
                .checkedAt(now)
                .awardedPoints(session.getRewardPoints())
                .note(request.getNote())
                .checkInLocation(userLocation)
                .deviceInfo(request.getDeviceInfo())
                .build();

        attendance = attendanceRepository.save(attendance);

        log.info("출석 체크인 완료: 사용자={}, 상태={}", user.getName(), status);

        return convertToResponse(attendance);
    }

    /**
     * Retrieve attendance records for the specified session ordered by check-in time.
     *
     * @param sessionId the UUID of the attendance session to query
     * @return a list of AttendanceResponse objects ordered by checkedAt ascending
     * @throws IllegalArgumentException if the session with the given ID does not exist
     */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceBySession(UUID sessionId) {
        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        List<Attendance> attendances = attendanceRepository.findByAttendanceSessionOrderByCheckedAtAsc(session);

        return attendances.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all attendance records for the given user ordered by checkedAt descending.
     *
     * @param user the user whose attendance records to retrieve
     * @return a list of AttendanceResponse objects ordered by checkedAt descending
     */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendancesByUser(User user) {
        List<Attendance> attendances = attendanceRepository.findByUserOrderByCheckedAtDesc(user);

        return attendances.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update a member's attendance status for a specific session.
     *
     * Updates the attendance record's status (e.g., PRESENT, LATE, ABSENT), records the provided reason, persists the change, and returns the updated representation.
     *
     * @param sessionId the identifier of the attendance session
     * @param memberId  the identifier of the member whose attendance will be updated
     * @param status    the new attendance status as a string (case-insensitive; must match an AttendanceStatus enum value)
     * @param reason    an optional reason or note explaining the status change
     * @param adminUser the administrator performing the update
     * @return the updated AttendanceResponse reflecting the persisted changes
     * @throws IllegalArgumentException if the session does not exist, the member's attendance is not found, or the provided status is invalid
     */
    public AttendanceResponse updateAttendanceStatus(UUID sessionId, UUID memberId, String status, String reason, User adminUser) {
        log.info("출석 상태 수정 시작: 세션ID={}, 멤버ID={}, 새로운상태={}, 관리자={}", sessionId, memberId, status, adminUser.getName());

        // 세션 존재 확인
        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        // 해당 세션에서 해당 멤버의 출석 기록 찾기
        Attendance attendance = attendanceRepository.findByAttendanceSessionAndUser_UserId(session, memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 세션에서 멤버의 출석 기록을 찾을 수 없습니다: " + memberId));

        AttendanceStatus newStatus;
        try {
            newStatus = AttendanceStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("잘못된 출석 상태입니다: " + status);
        }

        attendance.updateStatus(newStatus, reason);
        attendance = attendanceRepository.save(attendance);

        log.info("출석 상태 수정 완료: 세션ID={}, 멤버ID={}, 상태={}", sessionId, memberId, newStatus);

        return convertToResponse(attendance);
    }

    /**
     * Convert an Attendance entity to an AttendanceResponse DTO.
     *
     * @param attendance the Attendance entity to convert
     * @return an AttendanceResponse populated with the attendance's identifiers, user and session info,
     *         status, timestamps, awarded points, note, device information, late flag, and optional
     *         check-in latitude/longitude (null if no check-in location)
     */
    private AttendanceResponse convertToResponse(Attendance attendance) {
        return AttendanceResponse.builder()
                .attendanceId(attendance.getAttendanceId())
                .userId(attendance.getUser().getUserId())
                .userName(attendance.getUser().getName())
                .attendanceSessionId(attendance.getAttendanceSession().getAttendanceSessionId())
                .attendanceStatus(attendance.getAttendanceStatus())
                .checkedAt(attendance.getCheckedAt())
                .awardedPoints(attendance.getAwardedPoints())
                .note(attendance.getNote())
                .checkInLatitude(attendance.getCheckInLocation() != null ?
                        attendance.getCheckInLocation().getLat() : null)
                .checkInLongitude(attendance.getCheckInLocation() != null ?
                        attendance.getCheckInLocation().getLng() : null)
                .deviceInfo(attendance.getDeviceInfo())
                .isLate(attendance.isLate())
                .createdAt(attendance.getCreatedDate())
                .updatedAt(attendance.getUpdatedDate())
                .build();
    }

}