package org.sejongisc.backend.attendance.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceResponse;
import org.sejongisc.backend.attendance.dto.AttendanceRoundQrTokenRequest;
import org.sejongisc.backend.attendance.entity.Attendance;
import org.sejongisc.backend.attendance.entity.AttendanceRound;
import org.sejongisc.backend.attendance.entity.AttendanceStatus;
import org.sejongisc.backend.attendance.repository.AttendanceRepository;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
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
   * QR 토큰 기반 출석 체크인 처리(세션 멤버용) - qrToken으로 라운드 검증/조회 (HMAC + 만료 + ACTIVE) - 세션 멤버십 및 중복 출석 방지 - 지각 판별 및 출석 상태 결정
   */
  public void checkIn(UUID userId, AttendanceRoundQrTokenRequest request) {

    // 토큰 검증 + ACTIVE 라운드 조회
    AttendanceRound round = attendanceRoundService.verifyQrTokenAndGetRound(request.qrToken());

    // 세션 멤버 체크
    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureMember(sessionId, userId);

    User userRef = userRepository.getReferenceById(userId);

    // 중복 출석 방지
    if (attendanceRepository.existsByUserAndAttendanceRound(userRef, round)) {
      throw new CustomException(ErrorCode.ALREADY_CHECKED_IN);
    }

    LocalDateTime now = LocalDateTime.now();

    String deviceId = request.deviceId();
    if (deviceId == null || deviceId.isBlank()) {
      log.error("Device ID 누락: userId={}", userId);
      throw new CustomException(ErrorCode.DEVICE_ID_REQUIRED);
    }

    // 대리 출석 방지
    if (attendanceRepository.existsByAttendanceRound_RoundIdAndDeviceId(round.getRoundId(), request.deviceId())) {
      log.error("Device ID 중복 출석 시도: userId={}, deviceId={}", userId, request.deviceId());
      throw new CustomException(ErrorCode.DEVICE_ALREADY_USED);
    }

    Attendance att = Attendance.builder()
        .user(userRef)
        .attendanceRound(round)
        .attendanceStatus(decideLate(round, now) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT)
        .deviceId(request.deviceId())
        .checkedAt(now)
        .build();

    try {
      attendanceRepository.save(att);
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.ALREADY_CHECKED_IN);
    }
  }

  /**
   * 라운드별 출석 목록 조회 (관리자/OWNER)
   */
  @Transactional(readOnly = true)
  public List<AttendanceResponse> getAttendancesByRound(UUID roundId, UUID requesterUserId) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new CustomException(ErrorCode.ROUND_NOT_FOUND));

    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureAdmin(sessionId, requesterUserId);

    return attendanceRepository.findByAttendanceRound_RoundId(roundId)
        .stream()
        .map(AttendanceResponse::from)
        .toList();
  }

  /**
   * 라운드 기반 출석 상태 수정 (관리자/OWNER) - roundId, targetUserId, status, reason - 기존 기록 없으면 새로 생성(예: 결석 처리)
   */
  public AttendanceResponse updateAttendanceStatusByRound(
      UUID adminUserId,
      UUID roundId,
      UUID targetUserId,
      String status,
      String reason
  ) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new CustomException(ErrorCode.ROUND_NOT_FOUND));

    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureAdmin(sessionId, adminUserId);

    User targetUser = userRepository.findById(targetUserId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    AttendanceStatus newStatus = parseStatus(status);

    Attendance attendance = attendanceRepository.findByAttendanceRound_RoundIdAndUser(roundId, targetUser)
        .orElse(null);

    if (attendance == null) {
      attendance = Attendance.builder()
          .user(targetUser)
          .attendanceRound(round)
          .attendanceStatus(newStatus)
          .note(reason)
          .deviceId("ADMIN_UPDATE_" + UUID.randomUUID()) // 관리자 수정 출석은 임의의 디바이스 ID 사용
          .checkedAt(LocalDateTime.now())
          .build();
    } else {
      attendance.changeStatus(newStatus, reason);
    }
    return AttendanceResponse.from(attendanceRepository.save(attendance));
  }


  @Transactional(readOnly = true)
  public List<AttendanceResponse> getAttendancesByUser(UUID userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    List<Attendance> attendances = attendanceRepository.findByUserOrderByCheckedAtDesc(user);

    return attendances.stream()
        .map(AttendanceResponse::from)
        .collect(Collectors.toList());
  }

  // ----------------- helpers -----------------

  private AttendanceStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      throw new CustomException(ErrorCode.STATUS_REQUIRED);
    }
    try {
      return AttendanceStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new CustomException(ErrorCode.INVALID_ATTENDANCE_STATUS);
    }
  }

  private boolean decideLate(AttendanceRound round, LocalDateTime checkedAt) {
    var threshold = round.getStartAt().plusMinutes(5);
    return checkedAt.isAfter(threshold);
  }
}
