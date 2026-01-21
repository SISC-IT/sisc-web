package org.sejongisc.backend.attendance.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceResponse;
import org.sejongisc.backend.attendance.entity.Attendance;
import org.sejongisc.backend.attendance.entity.AttendanceRound;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.AttendanceStatus;
import org.sejongisc.backend.attendance.entity.RoundStatus;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.attendance.repository.SessionUserRepository;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceRoundRepository attendanceRoundRepository;
    private final UserRepository userRepository;
    private final AttendanceAuthorizationService authorizationService;
    private final AttendanceRoundService attendanceRoundService;



    /**
     * QR 토큰 기반 출석 체크인 처리(세션 멤버용)
     * - qrToken으로 라운드 검증/조회 (HMAC + 만료 + ACTIVE)
     * - 세션 멤버십 및 중복 출석 방지
     * - 지각 판별 및 출석 상태 결정
     */
    public void checkIn(UUID userId, String qrToken) {

        // 토큰 검증 + ACTIVE 라운드 조회
        AttendanceRound round = attendanceRoundService.verifyQrTokenAndGetRound(qrToken);

        // 세션 멤버 체크
        UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
        authorizationService.ensureMember(sessionId, userId);

        User userRef = userRepository.getReferenceById(userId);

        // 중복 출석 방지
        if (attendanceRepository.existsByUserAndAttendanceRound(userRef, round)) {
            throw new IllegalStateException("ALREADY_CHECKED_IN");
        }

        LocalDateTime now = LocalDateTime.now();

        Attendance att = Attendance.builder()
            .user(userRef)
            .attendanceRound(round)
            .attendanceStatus(decideLate(round, now) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT)
            .checkedAt(now)
            .build();

        try {
            attendanceRepository.save(att);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("ALREADY_CHECKED_IN");
        }
    }

    /**
     * 라운드별 출석 목록 조회 (관리자/OWNER)
     */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendancesByRound(UUID roundId, UUID requesterUserId) {
        AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
            .orElseThrow(() -> new IllegalArgumentException("ROUND_NOT_FOUND"));

        UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
        authorizationService.ensureAdmin(sessionId, requesterUserId);

        return attendanceRepository.findByAttendanceRound_RoundId(roundId)
            .stream()
            .map(AttendanceResponse::from)
            .toList();
    }

    /**
     * 라운드 기반 출석 상태 수정 (관리자/OWNER)
     * - roundId, targetUserId, status, reason
     * - 기존 기록 없으면 새로 생성(예: 결석 처리)
     */
    public AttendanceResponse updateAttendanceStatusByRound(
        UUID adminUserId,
        UUID roundId,
        UUID targetUserId,
        String status,
        String reason
    ) {
        AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
            .orElseThrow(() -> new IllegalArgumentException("ROUND_NOT_FOUND"));

        UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
        authorizationService.ensureAdmin(sessionId, adminUserId);

        User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));

        AttendanceStatus newStatus = parseStatus(status);

        Attendance attendance = attendanceRepository.findByAttendanceRound_RoundIdAndUser(roundId, targetUser)
            .orElse(null);

        if (attendance == null) {
            attendance = Attendance.builder()
                .user(targetUser)
                .attendanceRound(round)
                .attendanceStatus(newStatus)
                .note(reason)
                .checkedAt(LocalDateTime.now()) // checkedAt을 수동으로 넣고 싶으면 @CreationTimestamp 제거 권장
                .build();
        } else {
            attendance.changeStatus(newStatus, reason); // ✅ 엔티티 메서드로 변경
        }
        return AttendanceResponse.from(attendanceRepository.save(attendance));
    }



    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendancesByUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));

        List<Attendance> attendances = attendanceRepository.findByUserOrderByCheckedAtDesc(user);

        return attendances.stream()
            .map(AttendanceResponse::from)
            .collect(Collectors.toList());
    }

    // ----------------- helpers -----------------

    private AttendanceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) throw new IllegalArgumentException("STATUS_REQUIRED");
        try {
            return AttendanceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("INVALID_ATTENDANCE_STATUS");
        }
    }



    private boolean decideLate(AttendanceRound round, LocalDateTime checkedAt) {
        var threshold = round.getStartAt().plusMinutes(5);
        return checkedAt.isAfter(threshold);
    }









    //    /**
//     * 라운드 기반 출석 체크인 처리
//     * - 특정 라운드의 시간 및 위치 검증
//     * - 지각 판별 및 출석 상태 결정
//     */
//    public AttendanceCheckInResponse checkInByRound(AttendanceCheckInRequest request, UUID userId) {
//        // 사용자 조회
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
//
//        AttendanceRound round = attendanceRoundRepository.findRoundById(request.getRoundId())
//                .orElseThrow(() -> new IllegalArgumentException("라운드를 찾을 수 없습니다: " + request.getRoundId()));
//
//        AttendanceSession session = round.getAttendanceSession();
//
//        log.info("라운드 출석 체크인 시작: 사용자={}, 라운드ID={}, 날짜={}",
//                user.getName(), request.getRoundId(), round.getRoundDate());
//
//        // 라운드 시간 검증 - 통일된 로직
//        LocalDateTime now = LocalDateTime.now();
//        LocalDate checkDate = now.toLocalDate();
//        LocalTime checkTime = now.toLocalTime();
//        LocalTime startTime = round.getStartTime();
//        LocalTime endTime = round.getEndTime();
//        LocalTime lateThreshold = startTime.plusMinutes(5);
//
//        // 날짜 검증
//        if (!checkDate.equals(round.getRoundDate())) {
//            log.warn("❌ 출석 날짜 불일치: 라운드ID={}, 사용자={}, 현재시간={}, 라운드날짜={}",
//                    request.getRoundId(), user.getName(), now, round.getRoundDate());
//            return AttendanceCheckInResponse.builder()
//                    .roundId(request.getRoundId())
//                    .success(false)
//                    .failureReason("출석 날짜가 맞지 않습니다")
//                    .build();
//        }
//
//        // 시간 범위 검증: startTime <= now < endTime
//        boolean isWithinTimeWindow = !checkTime.isBefore(startTime) && checkTime.isBefore(endTime);
//        if (!isWithinTimeWindow) {
//            log.warn("❌ 출석 시간 초과: 라운드ID={}, 사용자={}, 현재시간={}, 시작={}, 종료={}",
//                    request.getRoundId(), user.getName(), now, startTime, endTime);
//            return AttendanceCheckInResponse.builder()
//                    .roundId(request.getRoundId())
//                    .success(false)
//                    .failureReason("출석 시간 초과")
//                    .build();
//        }
//
//        log.info("✅ 시간 검증 성공: 라운드ID={}, 사용자={}, 현재시간={}, 범위=[{}~{}]",
//                request.getRoundId(), user.getName(), now, startTime, endTime);
//
//        // 2. 기존 출석 기록 확인 (PENDING 제외하고 실제 체크인한 기록만 중복으로 취급)
//        Attendance existingAttendance = attendanceRepository.findByAttendanceRound_RoundIdAndUser(request.getRoundId(), user)
//                .orElse(null);
//        if (existingAttendance != null && existingAttendance.getAttendanceStatus() != AttendanceStatus.PENDING) {
//            log.warn("중복 출석 시도: 라운드ID={}, 사용자={}, 기존상태={}",
//                    request.getRoundId(), user.getName(), existingAttendance.getAttendanceStatus());
//            return AttendanceCheckInResponse.builder()
//                    .roundId(request.getRoundId())
//                    .success(false)
//                    .failureReason("이미 출석 체크인하셨습니다")
//                    .build();
//        }
//
//        // 3. 위치 검증 (세션에 위치 정보가 있는 경우)
//        Location userLocation = null;
//        if (session.getLocation() != null) {
//            if (request.getLatitude() == null || request.getLongitude() == null) {
//                log.warn("위치 정보 누락: 라운드ID={}, 사용자={}", request.getRoundId(), user.getName());
//                return AttendanceCheckInResponse.builder()
//                        .roundId(request.getRoundId())
//                        .success(false)
//                        .failureReason("위치 정보가 필요합니다")
//                        .build();
//            }
//
//            userLocation = Location.builder()
//                    .lat(request.getLatitude())
//                    .lng(request.getLongitude())
//                    .build();
//
//            if (!session.getLocation().isWithRange(userLocation)) {
//                log.warn("위치 불일치: 라운드ID={}, 사용자={}, 거리 초과",
//                        request.getRoundId(), user.getName());
//                return AttendanceCheckInResponse.builder()
//                        .roundId(request.getRoundId())
//                        .success(false)
//                        .failureReason("위치 불일치 - 허용 범위를 벗어났습니다")
//                        .build();
//            }
//        }
//
//        // 4. 출석 상태 판별 (정상/지각)
//        // 지각 기준: 시작시간 + 5분 이후면 LATE
//        AttendanceStatus status = checkTime.isAfter(lateThreshold) ?
//                AttendanceStatus.LATE : AttendanceStatus.PRESENT;
//
//        log.info("📊 출석 상태 판별: 현재시간={}, 시작={}, 지각기준={}, 판별상태={}",
//                checkTime, startTime, lateThreshold, status);
//
//        // 5. 출석 기록 저장
//        Attendance attendance = Attendance.builder()
//                .user(user)
//                .attendanceRound(round)
//                .attendanceStatus(status)
//                .checkedAt(java.time.LocalDateTime.now())
//                .awardedPoints(session.getRewardPoints())
//                .checkInLocation(userLocation)
//                .build();
//
//        log.info("💾 Attendance 객체 생성 완료: 사용자={}, 라운드ID={}, 상태={}, 체크인시간={}",
//                user.getName(), request.getRoundId(), status, attendance.getCheckedAt());
//
//        attendance = attendanceRepository.save(attendance);
//
//        log.info("✅ Attendance 저장 완료: attendanceId={}, 사용자={}, 라운드ID={}, 상태={}",
//                attendance.getAttendanceId(), user.getName(), request.getRoundId(), status);
//
//        round.getAttendances().add(attendance);
//
//        log.info("✅ 라운드 출석 체크인 완료: 사용자={}, 상태={}, 저장된ID={}", user.getName(), status, attendance.getAttendanceId());
//
//        long remainingSeconds = java.time.Duration.between(
//                checkTime,
//                endTime
//        ).getSeconds();
//
//        return AttendanceCheckInResponse.builder()
//                .roundId(request.getRoundId())
//                .success(true)
//                .status(status.toString())
//                .checkedAt(attendance.getCheckedAt())
//                .awardedPoints(attendance.getAwardedPoints())
//                .remainingSeconds(Math.max(0, remainingSeconds))
//                .build();
//    }

}
