package org.sejongisc.backend.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceSessionRequest;
import org.sejongisc.backend.attendance.dto.AttendanceSessionResponse;
import org.sejongisc.backend.attendance.dto.SessionLocationUpdateRequest;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.Location;
import org.sejongisc.backend.attendance.entity.SessionStatus;
import org.sejongisc.backend.attendance.entity.SessionVisibility;
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
     * 출석 코드로 세션 조회
     * - 학생이 코드 입력 시 사용
     * - 체크인 가능 여부 자동 계산
     */
    @Transactional(readOnly = true)
    public AttendanceSessionResponse getSessionByCode(String code) {
        AttendanceSession session = attendanceSessionRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 출석 코드입니다: " + code));

        return convertToResponse(session);
    }

    /**
     * 모든 세션 목록 조회
     * - 관리자용, 공개/비공개 모두 포함
     * - 최신 순으로 정렬
     */
    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getAllSessions() {
        List<AttendanceSession> sessions = attendanceSessionRepository.findAllByOrderByStartsAtDesc();

        return sessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 태그별 세션 목록 조회
     * - "금융IT", "동아리 전체" 등 태그로 필터링
     */
    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getSessionsByTag(String tag) {
        List<AttendanceSession> sessions = attendanceSessionRepository.findByTag(tag);

        return sessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 상태별 세션 목록 조회
     * - UPCOMING/OPEN/CLOSED 상태별 필터링
     */
    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getSessionsByStatus(SessionStatus status) {
        List<AttendanceSession> sessions = attendanceSessionRepository.findByStatus(status);

        return sessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 공개 세션 목록 조회
     * - 학생들이 볼 수 있는 공개 세션만 조회
     * - 최신 순으로 정렬
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
     * 활성 세션 목록 조회
     * - 현재 체크인 가능한 세션들만 필터링
     * - 시작 시간 ~ 종료 시간 범위 내 세션
     */
    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getActiveSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<AttendanceSession> allSessions = attendanceSessionRepository.findAllByOrderByStartsAtDesc();

        return allSessions.stream()
                 .filter(session -> {
             if (session.getStatus() != SessionStatus.OPEN) {
                        return false;
                    }
             LocalDateTime endTime = session.getStartsAt().plusSeconds(session.getWindowSeconds());
                return !now.isBefore(session.getStartsAt()) && now.isBefore(endTime);
            })
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    /**
     * 세션 정보 수정
     * - 제목, 시간, 위치, 반경 등 수정 가능
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
     * - 시간과 관계없이 체크인 활성화
     */
    public void activateSession(UUID sessionId) {
        log.info("출석 세션 활성화 시작: 세션ID={}", sessionId);

        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        session = session.toBuilder()
                .status(SessionStatus.OPEN)
                .startsAt(LocalDateTime.now())
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
     * - 기본 세션 정보: 제목, 시작 시간, 출석 인정 시간, 보상 포인트
     * - 위치 정보: location 객체 (lat, lng)
     * - 공개 여부: isVisible boolean
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

        // defaultAvailableMinutes: windowSeconds를 분 단위로 변환
        Integer defaultAvailableMinutes = (int) (session.getWindowSeconds() / 60);

        // defaultStartTime: startsAt에서 시간 부분만 추출
        LocalTime defaultStartTime = session.getStartsAt().toLocalTime();

        // isVisible: 공개 세션이면 true, 비공개면 false
        Boolean isVisible = session.getVisibility() == SessionVisibility.PUBLIC;

        return AttendanceSessionResponse.builder()
                .attendanceSessionId(session.getAttendanceSessionId())
                .title(session.getTitle())
                .location(location)
                .defaultStartTime(defaultStartTime)
                .defaultAvailableMinutes(defaultAvailableMinutes)
                .rewardPoints(session.getRewardPoints())
                .isVisible(isVisible)
                .build();

    }

}
