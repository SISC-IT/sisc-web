package org.sejongisc.backend.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceSessionRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionResponse;
import org.sejongisc.backend.attendance.dto.SessionLocationUpdateRequest;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.Location;
import org.sejongisc.backend.attendance.entity.SessionStatus;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
     * 출석 세션 생성
     * - 6자리 유니크 코드 자동 생성
     * - GPS 위치 및 반경 설정 (선택사항)
     * - 기본 상태 UPCOMING 으로 설정
     */
    public AttendanceSessionResponse createSession(AttendanceSessionRequest request) {
        log.info("출석 세션 생성 시작: 제목={}, 기본시간={}, 출석인정시간={}분",
                request.getTitle(), request.getDefaultStartTime(), request.getAllowedMinutes());


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
                .defaultStartTime(request.getDefaultStartTime())
                .allowedMinutes(request.getAllowedMinutes())
                .code(code)
                .rewardPoints(request.getRewardPoints())
                .location(location)
                .status(SessionStatus.UPCOMING)
                .build();

        session = attendanceSessionRepository.save(session);

        log.info("출석 세션 생성 완료: 세션ID={}, 코드={}", session.getAttendanceSessionId(), code);

        return convertToResponse(session);
    }

    /**
     * 세션 ID로 상세 정보 조회
     * - 남은 시간, 참여자 수 등 계산된 정보 포함
     */
    @Transactional(readOnly = true)
    public AttendanceSessionResponse getSessionById(UUID sessionId) {
        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        return convertToResponse(session);
    }

    /**
     * 모든 세션 목록 조회
     * - 관리자용, 공개/비공개 모두 포함
     * - 생성 순으로 정렬
     */
    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getAllSessions() {
        List<AttendanceSession> sessions = attendanceSessionRepository.findAll();

        return sessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 공개 세션 목록 조회
     * - 학생들이 볼 수 있는 모든 세션만 조회
     * - 생성 순으로 정렬
     */
    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getPublicSessions() {
        List<AttendanceSession> sessions = attendanceSessionRepository.findAll();

        return sessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 활성 세션 목록 조회
     * - 현재 체크인 가능한 세션들만 필터링 (라운드 기반)
     * - 세션의 상태가 OPEN인 세션들만 반환
     */
    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getActiveSessions() {
        List<AttendanceSession> allSessions = attendanceSessionRepository.findAll();

        return allSessions.stream()
                .filter(session -> session.getStatus() == SessionStatus.OPEN)
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 세션 정보 수정
     * - 제목, 기본시간, 출석인정시간, 위치, 반경 등 수정 가능
     * - 코드는 변경되지 않음 (보안상 이유)
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
        }else {
            location = session.getLocation();
        }

        session = session.toBuilder()
                .title(request.getTitle())
                .defaultStartTime(request.getDefaultStartTime())
                .allowedMinutes(request.getAllowedMinutes())
                .rewardPoints(request.getRewardPoints())
                .location(location)
                .build();

        session = attendanceSessionRepository.save(session);

        log.info("출석 세션 수정 완료: 세션ID={}", sessionId);

        return convertToResponse(session);
    }

    /**
     * 세션 완전 삭제
     * - CASCADE 관련 출석 기록도 함께 삭제
     * - 주의: 복구 불가능
     */
    public void deleteSession(UUID sessionId) {
        log.info("출석 세션 삭제 시작: 세션ID={}", sessionId);

        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        attendanceSessionRepository.delete(session);

        log.info("출석 세션 삭제 완료: 세션ID={}", sessionId);
    }

    /**
     * 세션 수동 활성화
     * - 세션 상태를 OPEN으로 변경
     * - 라운드 기반이므로 세션 상태만 변경
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
     * 세션 수동 종료
     * - 세션 상태를 CLOSED로 변경
     * - 체크인 비활성화
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
     * 중복되지 않는 6자리 코드 생성
     * - DB에서 중복 검사 후 유니크 코드 리턴
     */
    private String generateUniqueCode() {
        String code;
        do {
            code = generateRandomCode();
        } while (attendanceSessionRepository.existsByCode(code));
        return code;
    }

    /**
     * 세션 위치 재설정
     * - 기존 위치 정보를 새로운 위치로 업데이트
     * - 반경은 기존 값 유지 또는 0으로 설정
     */
    public AttendanceSessionResponse updateSessionLocation(UUID sessionId, SessionLocationUpdateRequest request) {
        log.info("세션 위치 재설정 시작: 세션ID={}, 위도={}, 경도={}",
                sessionId, request.getLatitude(), request.getLongitude());

        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        Location newLocation = Location.builder()
                .lat(request.getLatitude())
                .lng(request.getLongitude())
                .radiusMeters(session.getLocation() != null ?
                        session.getLocation().getRadiusMeters() : 100)
                .build();

        session = session.toBuilder()
                .location(newLocation)
                .build();

        session = attendanceSessionRepository.save(session);

        log.info("세션 위치 재설정 완료: 세션ID={}", sessionId);

        return convertToResponse(session);
    }

    /**
     * 6자리 랜덤 숫자 코드 생성
     * - 000000 ~ 999999 범위 내 랜덤 생성
     */
    private String generateRandomCode() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * AttendanceSession 엔티티를 Response DTO로 변환
     * - 기본 세션 정보: 제목, 기본 시작 시간, 출석 인정 시간, 보상 포인트
     * - 위치 정보: location 객체 (lat, lng)
     */
    private AttendanceSessionResponse convertToResponse(AttendanceSession session) {
        // 위치 정보 변환 (location이 존재하면 LocationInfo 객체 생성, 없으면 null)
        AttendanceSessionResponse.LocationInfo location = null;
        if (session.getLocation() != null) {
            location = AttendanceSessionResponse.LocationInfo.builder()
                    .lat(session.getLocation().getLat())
                    .lng(session.getLocation().getLng())
                    .build();
        }

        return AttendanceSessionResponse.builder()
                .attendanceSessionId(session.getAttendanceSessionId())
                .title(session.getTitle())
                .location(location)
                .defaultStartTime(session.getDefaultStartTime())
                .defaultAvailableMinutes(session.getAllowedMinutes())
                .rewardPoints(session.getRewardPoints())
                .build();

    }

}
