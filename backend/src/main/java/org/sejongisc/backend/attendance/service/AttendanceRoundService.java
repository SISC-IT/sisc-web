package org.sejongisc.backend.attendance.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceRoundQrTokenResponse;
import org.sejongisc.backend.attendance.dto.AttendanceRoundRequest;
import org.sejongisc.backend.attendance.dto.AttendanceRoundResponse;
import org.sejongisc.backend.attendance.entity.AttendanceRound;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.RoundStatus;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.attendance.util.QrTokenUtil;
import org.sejongisc.backend.attendance.util.RollingQrTokenUtil;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AttendanceRoundService {

  private static final int DEFAULT_ROUND_DURATION_HOURS = 3;

  private final AttendanceRoundRepository attendanceRoundRepository;
  private final AttendanceSessionRepository attendanceSessionRepository;
  private final AttendanceAuthorizationService authorizationService;
  private final QrTokenStreamService qrTokenStreamService;

  /**
   * 라운드 생성(예약) - 세션 주인(OWNER)만 가능
   */
  public AttendanceRoundResponse createRound(UUID sessionId, UUID userId, AttendanceRoundRequest req) {
    authorizationService.ensureOwner(sessionId, userId);

    AttendanceSession session = attendanceSessionRepository.findById(sessionId)
        .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

    validateCreateRequest(req);

    LocalDateTime closeAt = (req.closeAt() != null)
        ? req.closeAt()
        : req.startAt().plusHours(DEFAULT_ROUND_DURATION_HOURS);

    AttendanceRound round = AttendanceRound.builder()
        .attendanceSession(session)
        .roundStatus(RoundStatus.UPCOMING)
        .roundDate(req.roundDate())
        .startAt(req.startAt())
        .closeAt(closeAt)
        .roundName(req.roundName())
        .locationName(req.locationName())
        // 라운드마다 고유 secret (절대 클라이언트에 노출 X)
        .qrSecret(QrTokenUtil.generateSecret())
        .build();

    AttendanceRound saved = attendanceRoundRepository.save(round);
    return AttendanceRoundResponse.from(saved, false);
  }

  /** 라운드 개별 조회(세션 멤버만) */
  @Transactional(readOnly = true)
  public AttendanceRoundResponse getRound(UUID roundId, UUID userId) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new CustomException(ErrorCode.ROUND_NOT_FOUND));

    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureMember(sessionId, userId);

    return AttendanceRoundResponse.from(round, false);
  }

  /** 세션 내 라운드 목록 조회(세션 멤버만) */
  @Transactional(readOnly = true)
  public List<AttendanceRoundResponse> getRoundsBySession(UUID sessionId, UUID userId) {
    authorizationService.ensureMember(sessionId, userId);

    List<AttendanceRound> rounds = attendanceRoundRepository
        .findByAttendanceSession_AttendanceSessionIdOrderByRoundDateAsc(sessionId);

    return rounds.stream()
        .map(r -> AttendanceRoundResponse.from(r, false))
        .toList();
  }

  /**
   * (fallback) 단일 호출로 현재 3분 윈도우 QR 토큰 발급 (관리자/OWNER)
   * - 폴링이 싫으면 SSE 스트림(/qr-stream) 사용 권장
   */
  @Transactional(readOnly = true)
  public AttendanceRoundQrTokenResponse issueQrToken(UUID roundId, UUID userId) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new CustomException(ErrorCode.ROUND_NOT_FOUND));

    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureAdmin(sessionId, userId);

    if (round.getRoundStatus() != RoundStatus.ACTIVE) {
      throw new CustomException(ErrorCode.ROUND_NOT_ACTIVE);
    }

    RollingQrTokenUtil.IssuedToken issued = RollingQrTokenUtil.issue(roundId, round.getQrSecret());
    String qrUrl = qrTokenStreamService.createQrUrl(roundId, issued.token());

    return new AttendanceRoundQrTokenResponse(round.getRoundId(), qrUrl, issued.expiresAtEpochSec());
  }

  /**
   * 참가자 출석 처리 쪽에서 사용:
   * - 토큰에서 roundId만 먼저 추출 → 라운드 조회 → ACTIVE 체크 → secret으로 서명/윈도우 검증
   */
  @Transactional(readOnly = true)
  public AttendanceRound verifyQrTokenAndGetRound(String qrToken) {
    if (qrToken == null || qrToken.isBlank()) {
      throw new CustomException(ErrorCode.QR_TOKEN_MALFORMED);
    }

    String[] parts = qrToken.split(":");
    if (parts.length != 3) {
      throw new CustomException(ErrorCode.QR_TOKEN_MALFORMED);
    }

    UUID roundId;
    try {
      roundId = UUID.fromString(parts[0]);
    } catch (Exception e) {
      throw new CustomException(ErrorCode.QR_TOKEN_MALFORMED);
    }

    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new CustomException(ErrorCode.ROUND_NOT_FOUND));

    if (round.getRoundStatus() != RoundStatus.ACTIVE) {
      throw new CustomException(ErrorCode.ROUND_NOT_ACTIVE);
    }

    try {
      RollingQrTokenUtil.verifyAndParse(qrToken, round.getQrSecret());
    } catch (IllegalArgumentException e) {
      throw new CustomException(ErrorCode.QR_TOKEN_MALFORMED);
    } catch (IllegalStateException e) {
      throw new CustomException(ErrorCode.QR_TOKEN_MALFORMED);
    }

    return round;
  }

  /** 라운드 삭제(관리자/OWNER) */
  public void deleteRound(UUID roundId, UUID userId) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new CustomException(ErrorCode.ROUND_NOT_FOUND));

    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureAdmin(sessionId, userId);

    attendanceRoundRepository.delete(round);
    log.info("라운드 삭제 완료 - roundId: {}", roundId);
  }

  /** 라운드 마감(관리자/OWNER) */
  public void closeRound(UUID roundId, UUID userId) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new CustomException(ErrorCode.ROUND_NOT_FOUND));

    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureAdmin(sessionId, userId);

    round.changeStatus(RoundStatus.CLOSED);
  }

  /** 라운드 활성화(관리자/OWNER) */
  public void openRound(UUID roundId, UUID userId) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new CustomException(ErrorCode.ROUND_NOT_FOUND));

    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureAdmin(sessionId, userId);

    round.changeStatus(RoundStatus.ACTIVE);
  }

  /**
   * Quartz Job에서 호출: UPCOMING -> ACTIVE / ACTIVE -> CLOSED 자동 전환
   * (cron: 0분, 30분마다 실행)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void runRoundStatusMaintenance() {
    LocalDateTime now = LocalDateTime.now();
    int closed = attendanceRoundRepository.closeDueRounds(now);
    int activated = attendanceRoundRepository.activateDueRounds(now);

    if (activated > 0 || closed > 0) {
      log.info("[Quartz] activated={}, closed={}", activated, closed);
    }
  }

  private void validateCreateRequest(AttendanceRoundRequest req) {
    if (req.roundDate() == null) throw new CustomException(ErrorCode.ROUND_DATE_REQUIRED);
    if (req.startAt() == null) throw new CustomException(ErrorCode.START_AT_REQUIRED);
    if (req.roundName() == null || req.roundName().isBlank()) throw new CustomException(ErrorCode.ROUND_NAME_REQUIRED);
    if (req.closeAt() != null && !req.closeAt().isAfter(req.startAt())) {
      throw new CustomException(ErrorCode.END_AT_MUST_BE_AFTER_START_AT);
    }
  }
}
