package org.sejongisc.backend.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceCheckInRequest;
import org.sejongisc.backend.attendance.dto.AttendanceCheckInResponse;
import org.sejongisc.backend.attendance.dto.AttendanceResponse;
import org.sejongisc.backend.attendance.entity.*;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AttendanceRoundRepository attendanceRoundRepository;
    private final UserRepository userRepository;

    /**
     * 라운드 기반 출석 체크인 처리
     * - 특정 라운드의 시간 및 위치 검증
     * - 지각 판별 및 출석 상태 결정
     */
    public AttendanceCheckInResponse checkInByRound(AttendanceCheckInRequest request, UUID userId) {
        // 사용자가 존재하면 조회, 없으면 null (익명 사용자 지원)
        User user = userRepository.findById(userId).orElse(null);

        AttendanceRound round = attendanceRoundRepository.findRoundById(request.getRoundId())
                .orElseThrow(() -> new IllegalArgumentException("라운드를 찾을 수 없습니다: " + request.getRoundId()));

        AttendanceSession session = round.getAttendanceSession();

        // 익명사용자의 이름 결정
        String anonymousName = null;
        if (user == null) {
            // 사용자가 이름을 입력한 경우 사용
            if (request.getUserName() != null && !request.getUserName().trim().isEmpty()) {
                anonymousName = request.getUserName();
            } else {
                // 이름 미입력 시 자동 생성 (익명사용자-UUID의 처음 8글자)
                anonymousName = "익명사용자-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
        }

        String userName = user != null ? user.getName() : anonymousName;

        log.info("라운드 출석 체크인 시작: 사용자={}, 라운드ID={}, 날짜={}, 익명여부={}",
                userName, request.getRoundId(), round.getRoundDate(), user == null);

        // 1. 라운드 시간 검증 - 상세 로깅
        java.time.LocalTime checkTime = java.time.LocalTime.now();
        java.time.LocalDate checkDate = java.time.LocalDate.now();
        java.time.LocalTime endTime = round.getEndTime();
        java.time.LocalTime startTime = round.getStartTime();

        // 날짜 검증
        boolean dateMatch = checkDate.equals(round.getRoundDate());
        // 시간 검증: startTime <= now < endTime
        boolean timeInRange = !checkTime.isBefore(startTime) && checkTime.isBefore(endTime);

        log.info("📋 시간 검증 상세: 현재날짜={}, 라운드날짜={}, 날짜일치={} | 현재시간={}, 시작={}, 종료={}, 시간범위내={}",
                checkDate, round.getRoundDate(), dateMatch, checkTime, startTime, endTime, timeInRange);

        if (!round.isCheckInAvailable()) {
            log.warn("❌ 출석 시간 초과: 라운드ID={}, 사용자={}, 현재시간={}, 시작시간={}, 종료시간={}, 현재날짜={}, 라운드날짜={}, 이유: 날짜일치={}|시간범위={}",
                    request.getRoundId(), userName, checkTime, startTime, endTime, checkDate, round.getRoundDate(), dateMatch, timeInRange);
            return AttendanceCheckInResponse.builder()
                    .roundId(request.getRoundId())
                    .success(false)
                    .failureReason("출석 시간 초과")
                    .build();
        }

        log.info("✅ 시간 검증 성공: 라운드ID={}, 사용자={}, 라운드날짜={}, 라운드시작={}, 종료={}, 허용분={}, 현재시간={}",
                request.getRoundId(), userName, round.getRoundDate(), startTime, endTime, round.getAllowedMinutes(), checkTime);

        // 2. 중복 출석 확인 (인증된 사용자 또는 익명사용자 모두)
        if (user != null) {
            // 인증된 사용자: user ID로 중복 체크
            boolean alreadyCheckedIn = attendanceRepository.findByAttendanceRound_RoundIdAndUser(request.getRoundId(), user)
                    .isPresent();
            if (alreadyCheckedIn) {
                log.warn("중복 출석 시도: 라운드ID={}, 사용자={}", request.getRoundId(), userName);
                return AttendanceCheckInResponse.builder()
                        .roundId(request.getRoundId())
                        .success(false)
                        .failureReason("이미 출석 체크인하셨습니다")
                        .build();
            }
        } else if (request.getUserName() != null && !request.getUserName().trim().isEmpty()) {
            // 익명 사용자: 입력한 이름으로 중복 체크
            List<Attendance> existingAttendances = attendanceRepository.findByAttendanceRound_RoundId(request.getRoundId());
            boolean alreadyCheckedIn = existingAttendances.stream()
                    .anyMatch(a -> a.getUser() == null &&
                            request.getUserName().equalsIgnoreCase(a.getAnonymousUserName()));
            if (alreadyCheckedIn) {
                log.warn("익명사용자 중복 출석 시도: 라운드ID={}, 이름={}", request.getRoundId(), request.getUserName());
                return AttendanceCheckInResponse.builder()
                        .roundId(request.getRoundId())
                        .success(false)
                        .failureReason("이미 출석 체크인하셨습니다")
                        .build();
            }
        }

        // 3. 위치 검증 (세션에 위치 정보가 있는 경우)
        Location userLocation = null;
        if (session.getLocation() != null) {
            if (request.getLatitude() == null || request.getLongitude() == null) {
                log.warn("위치 정보 누락: 라운드ID={}, 사용자={}", request.getRoundId(), userName);
                return AttendanceCheckInResponse.builder()
                        .roundId(request.getRoundId())
                        .success(false)
                        .failureReason("위치 정보가 필요합니다")
                        .build();
            }

            userLocation = Location.builder()
                    .lat(request.getLatitude())
                    .lng(request.getLongitude())
                    .build();

            if (!session.getLocation().isWithRange(userLocation)) {
                log.warn("위치 불일치: 라운드ID={}, 사용자={}, 거리 초과",
                        request.getRoundId(), userName);
                return AttendanceCheckInResponse.builder()
                        .roundId(request.getRoundId())
                        .success(false)
                        .failureReason("위치 불일치 - 허용 범위를 벗어났습니다")
                        .build();
            }
        }

        // 4. 출석 상태 판별 (정상/지각)
        java.time.LocalTime now = java.time.LocalTime.now();
        java.time.LocalTime lateThreshold = round.getStartTime().plusMinutes(5);
        AttendanceStatus status = now.isAfter(lateThreshold) ?
                AttendanceStatus.LATE : AttendanceStatus.PRESENT;

        log.info("📊 출석 상태 판별: 현재시간={}, 시작시간={}, 지각기준={}, 판별상태={}",
                now, round.getStartTime(), lateThreshold, status);

        // 5. 출석 기록 저장
        Attendance attendance = Attendance.builder()
                .user(user)  // null 가능 (익명 사용자)
                .attendanceSession(session)
                .attendanceRound(round)
                .attendanceStatus(status)
                .checkedAt(java.time.LocalDateTime.now())
                .awardedPoints(session.getRewardPoints())
                .checkInLocation(userLocation)
                .anonymousUserName(user == null ? anonymousName : null)  // 익명사용자일 경우 이름 저장 (입력 또는 자동생성)
                .build();

        log.info("💾 Attendance 객체 생성 완료: 사용자={}, 라운드ID={}, 상태={}, 체크인시간={}, 익명이름={}",
                userName, request.getRoundId(), status, attendance.getCheckedAt(), anonymousName);

        attendance = attendanceRepository.save(attendance);

        log.info("✅ Attendance 저장 완료: attendanceId={}, 사용자={}, 라운드ID={}, 상태={}",
                attendance.getAttendanceId(), userName, request.getRoundId(), status);

        round.getAttendances().add(attendance);

        log.info("✅ 라운드 출석 체크인 완료: 사용자={}, 상태={}, 저장된ID={}", userName, status, attendance.getAttendanceId());

        long remainingSeconds = java.time.Duration.between(
                java.time.LocalTime.now(),
                round.getEndTime()
        ).getSeconds();

        return AttendanceCheckInResponse.builder()
                .roundId(request.getRoundId())
                .success(true)
                .status(status.toString())
                .checkedAt(attendance.getCheckedAt())
                .awardedPoints(attendance.getAwardedPoints())
                .remainingSeconds(Math.max(0, remainingSeconds))
                .build();
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
     * 라운드별 출석 목록 조회
     * - 특정 라운드의 모든 출석 기록 조회
     * - 출석 시간 순으로 정렬
     */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendancesByRound(java.util.UUID roundId) {
        log.info("📋 라운드별 출석 명단 조회 시작: roundId={}", roundId);

        AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
                .orElseThrow(() -> new IllegalArgumentException("라운드를 찾을 수 없습니다: " + roundId));

        List<Attendance> attendances = attendanceRepository.findByAttendanceRound_RoundId(roundId);

        log.info("📊 라운드별 출석 명단 조회 결과: roundId={}, 출석인원={}, 라운드날짜={}, 라운드상태={}",
                roundId, attendances.size(), round.getRoundDate(), round.getRoundStatus());

        for (Attendance a : attendances) {
            log.info("  - 출석기록: 사용자={}, 상태={}, 체크인={}, 포인트={}",
                    a.getUser() != null ? a.getUser().getName() : "익명",
                    a.getAttendanceStatus(),
                    a.getCheckedAt(),
                    a.getAwardedPoints());
        }

        return attendances.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 라운드 기반 출석 상태 수정 (관리자용)
     * - roundId, userId, status를 받아 해당 라운드의 출석 상태 변경
     * - 라운드가 없으면 새로 생성 (예: 결석 처리)
     */
    public AttendanceResponse updateAttendanceStatusByRound(UUID roundId, UUID userId, String status, String reason) {
        log.info("📝 라운드 기반 출석 상태 수정 시작: roundId={}, userId={}, status={}", roundId, userId, status);

        // 1. 라운드 존재 확인
        AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
                .orElseThrow(() -> new IllegalArgumentException("라운드를 찾을 수 없습니다: " + roundId));

        // 2. 사용자 존재 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 3. 상태 값 검증
        AttendanceStatus newStatus;
        try {
            newStatus = AttendanceStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("잘못된 출석 상태입니다: " + status);
        }

        log.info("✅ 유효성 검사 완료: roundId={}, userId={}, newStatus={}", roundId, userId, newStatus);

        // 4. 기존 출석 기록 조회
        Attendance attendance = attendanceRepository.findByAttendanceRound_RoundIdAndUser(roundId, user)
                .orElse(null);

        if (attendance == null) {
            // 기존 기록이 없으면 새로 생성 (예: 결석 처리)
            log.info("📌 새로운 Attendance 레코드 생성: 기존 기록 없음");

            attendance = Attendance.builder()
                    .user(user)
                    .attendanceSession(round.getAttendanceSession())
                    .attendanceRound(round)
                    .attendanceStatus(newStatus)
                    .note(reason != null ? reason : "관리자가 추가함")
                    .checkedAt(java.time.LocalDateTime.now())
                    .build();

            attendance = attendanceRepository.save(attendance);
            log.info("💾 새 Attendance 레코드 저장 완료: attendanceId={}", attendance.getAttendanceId());
        } else {
            // 기존 기록이 있으면 상태 업데이트
            log.info("📝 기존 Attendance 레코드 업데이트");

            attendance.updateStatus(newStatus, reason);
            attendance = attendanceRepository.save(attendance);
            log.info("✅ Attendance 상태 업데이트 완료: status={}", newStatus);
        }

        log.info("✅ 라운드 기반 출석 상태 수정 완료: roundId={}, userId={}, status={}",
                roundId, userId, newStatus);

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
                .userId(attendance.getUser() != null ? attendance.getUser().getUserId() : null)
                .userName(attendance.getUser() != null ? attendance.getUser().getName() :
                        (attendance.getAnonymousUserName() != null ? attendance.getAnonymousUserName() : "익명사용자"))
                .attendanceSessionId(attendance.getAttendanceSession().getAttendanceSessionId())
                .attendanceRoundId(attendance.getAttendanceRound() != null ?
                        attendance.getAttendanceRound().getRoundId() : null)
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
