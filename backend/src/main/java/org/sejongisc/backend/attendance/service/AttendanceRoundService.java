package org.sejongisc.backend.attendance.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceRoundQrTokenResponse;
import org.sejongisc.backend.attendance.dto.AttendanceRoundRequest;
import org.sejongisc.backend.attendance.dto.AttendanceRoundResponse;
import org.sejongisc.backend.attendance.entity.*;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;
import org.sejongisc.backend.attendance.util.QrTokenUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AttendanceRoundService {

  private static final int DEFAULT_ROUND_DURATION_HOURS = 3;
  private static final long QR_TOKEN_TTL_SECONDS = 90; // 60~120 추천

  private final AttendanceRoundRepository attendanceRoundRepository;
  private final AttendanceSessionRepository attendanceSessionRepository;
  private final AttendanceAuthorizationService authorizationService;

  /** 라운드 생성(관리자/소유자) */

  public AttendanceRoundResponse createRound(UUID sessionId, UUID userId, AttendanceRoundRequest req) {
    authorizationService.ensureAdmin(sessionId, userId);

    AttendanceSession session = attendanceSessionRepository.findById(sessionId)
        .orElseThrow(() -> new IllegalArgumentException("SESSION_NOT_FOUND"));

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
        .qrSecret(QrTokenUtil.generateSecret())
        .build();

    AttendanceRound saved = attendanceRoundRepository.save(round);

    // 목록/상세 응답에는 토큰을 넣지 않는 걸 추천(짧게 만료되므로)
    return AttendanceRoundResponse.from(saved, false);
  }

  /** 라운드 개별 조회(세션 멤버만) - 토큰은 별도 API로 발급 */
  @Transactional(readOnly = true)
  public AttendanceRoundResponse getRound(UUID roundId, UUID userId) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new IllegalArgumentException("ROUND_NOT_FOUND"));

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

  /** 관리자만: QR 토큰 발급(짧게 유효) */
  @Transactional(readOnly = true)
  public AttendanceRoundQrTokenResponse issueQrToken(UUID roundId, UUID userId) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new IllegalArgumentException("ROUND_NOT_FOUND"));

    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureAdmin(sessionId, userId);

    // 라운드가 ACTIVE일 때만 발급하고 싶으면 아래 체크 추가:
    if (round.getRoundStatus() != RoundStatus.ACTIVE) {
      throw new IllegalStateException("ROUND_NOT_ACTIVE");
    }

    QrTokenUtil.IssuedToken issued = QrTokenUtil.issue(round.getRoundId(), round.getQrSecret(), QR_TOKEN_TTL_SECONDS);
    return new AttendanceRoundQrTokenResponse(round.getRoundId(), issued.token(), issued.expiresAtEpochSec());
  }

  /** 참가자 출석 처리 쪽에서 사용: 토큰 검증 후 라운드 조회 */
  @Transactional(readOnly = true)
  public AttendanceRound verifyQrTokenAndGetRound(String qrToken) {
    // 토큰 파싱을 위해 roundId 먼저 뽑고 → 라운드 가져온 뒤 secret으로 검증
    String[] parts = qrToken.split(":");
    if (parts.length != 3) throw new IllegalStateException("QR_TOKEN_MALFORMED");

    UUID roundId;
    try {
      roundId = UUID.fromString(parts[0]);
    } catch (Exception e) {
      throw new IllegalStateException("QR_TOKEN_MALFORMED");
    }

    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new IllegalStateException("ROUND_NOT_FOUND"));

    // 라운드 상태 체크(선택이 아니라 사실상 필수)
    if (round.getRoundStatus() != RoundStatus.ACTIVE) {
      throw new IllegalStateException("ROUND_NOT_ACTIVE");
    }

    // 서명/만료 검증
    QrTokenUtil.verifyAndParse(qrToken, round.getQrSecret());
    return round;
  }

  /** 라운드 삭제(관리자/소유자) */
  public void deleteRound(UUID roundId, UUID userId) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new IllegalArgumentException("ROUND_NOT_FOUND"));

    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureAdmin(sessionId, userId);

    attendanceRoundRepository.delete(round);
    log.info("라운드 삭제 완료 - roundId: {}", roundId);
  }

  /** 라운드 마감(관리자/소유자) */
  public void closeRound(UUID roundId, UUID userId) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new IllegalArgumentException("ROUND_NOT_FOUND"));

    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureAdmin(sessionId, userId);

    round.changeStatus(RoundStatus.CLOSED);
  }

  /** 라운드 활성화(관리자/소유자) */
  public void openRound(UUID roundId, UUID userId) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new IllegalArgumentException("ROUND_NOT_FOUND"));

    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureAdmin(sessionId, userId);

    round.changeStatus(RoundStatus.ACTIVE);
  }



  @Scheduled(fixedRate = 10_000)
  public void autoActivateAndCloseRounds() {
    LocalDateTime now = LocalDateTime.now();
    int closed = attendanceRoundRepository.closeDueRounds(now);
    int activated = attendanceRoundRepository.activateDueRounds(now);

    if (activated > 0 || closed > 0) {
      log.info("activated={}, closed={}", activated, closed);
    }
  }

  private void validateCreateRequest(AttendanceRoundRequest req) {
    if (req.roundDate() == null) throw new IllegalArgumentException("ROUND_DATE_REQUIRED");
    if (req.startAt() == null) throw new IllegalArgumentException("START_AT_REQUIRED");
    if (req.roundName() == null || req.roundName().isBlank()) throw new IllegalArgumentException("ROUND_NAME_REQUIRED");
    if (req.closeAt() != null && !req.closeAt().isAfter(req.startAt())) {
      throw new IllegalArgumentException("END_AT_MUST_BE_AFTER_START_AT");
    }
  }
}
