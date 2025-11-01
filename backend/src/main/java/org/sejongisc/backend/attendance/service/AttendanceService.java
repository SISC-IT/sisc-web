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
import org.sejongisc.backend.user.dao.UserRepository;
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
    private final UserRepository userRepository;


    /**
     * 출석 체크인 처리
     * - 코드 유효성 및 중복 출석 방지
     * - GPS 위치 및 반경 검증
     * - 시간 윈도우 검증 및 지각 판별
     */
    public AttendanceResponse checkIn(UUID sessionId, AttendanceRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다: " + userId));
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

        // 위치 정보가 있는 세션에 대해서만 사용자 위치 생성 및 검증
        Location userLocation = null;
        if (session.getLocation() != null) {
            if (request.getLatitude() == null || request.getLongitude() == null) {
                    throw new IllegalArgumentException("위치 기반 출석에는 위도와 경도가 필요합니다");
            }

            userLocation = Location.builder()
                    .lat(request.getLatitude())
                    .lng(request.getLongitude())
                    .build();

            if (!session.getLocation().isWithRange(userLocation)) {
                throw new IllegalArgumentException("출석 허용 범위를 벗어났습니다");
            }
        }


        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(session.getStartsAt())) {
            throw new IllegalStateException("아직 출석 시간이 아닙니다");
        }

        LocalDateTime endTime = session.getEndsAt();
        if (now.isAfter(endTime)) {
            throw new IllegalStateException("출석 시간이 종료되었습니다");
        }

        // 시작 후 5분 이내는 정상 출석, 이후는 지각
        LocalDateTime lateThreshold = session.getStartsAt().plusMinutes(5);
        AttendanceStatus status = now.isAfter(lateThreshold) ?
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
     * 세션별 출석 목록 조회
     * - 관리자가 특정 세션의 모든 출석자 확인
     * - 출석 시간 순으로 정렬
     */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendancesBySession(UUID sessionId) {
        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        List<Attendance> attendances = attendanceRepository.findByAttendanceSessionOrderByCheckedAtAsc(session);

        return attendances.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 사용자별 출석 이력 조회
     * - 개인의 모든 출석 기록 조회
     * - 최신 순으로 정렬
     */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendancesByUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다: " + userId));
        List<Attendance> attendances = attendanceRepository.findByUserOrderByCheckedAtDesc(user);

        return attendances.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 출석 상태 수정(관리자용)
     * - PRESENT/LATE/ABSENT 등으로 상태 변경
     * - 수정 사유 기록 및 로그 남기기
     */
    public AttendanceResponse updateAttendanceStatus(UUID sessionId, UUID memberId, String status, String reason, UUID adminId) {
        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 관리자입니다: " + adminId));
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
     * Attendance 엔티티를 AttendanceResponse DTO로 변환
     * - 엔티티의 모든 필드를 Response 형태로 매핑
     * - 사용자 이름, 위치 정보, 지각 여부 포함
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
