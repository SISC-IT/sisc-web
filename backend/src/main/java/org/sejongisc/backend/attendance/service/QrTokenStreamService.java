package org.sejongisc.backend.attendance.service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.AttendanceRoundQrTokenResponse;
import org.sejongisc.backend.attendance.entity.AttendanceRound;
import org.sejongisc.backend.attendance.entity.RoundStatus;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.util.RollingQrTokenUtil;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.common.sse.SseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * QR 토큰을 폴링 없이 서버가 PUSH 해주는 SSE 스트림 서비스.
 *
 * - 관리자(OWNER/MANAGER)가 QR 화면을 열면 subscribe()
 * - 서버는 즉시 현재 윈도우 토큰을 보내고,
 * - 이후 윈도우 경계마다 자동으로 새로운 토큰을 push 합니다.
 *
 * 참고: 프록시/로드밸런서 환경에서 SSE 연결이 idle timeout으로 끊기지 않도록 ping 이벤트를 주기적으로 보냅니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QrTokenStreamService {

  @Value("${app.frontend-url}")
  private String frontendUrl;

  private static final String ATTENDANCE_PATH = "/attendance";
  private static final String QR_TOKEN_EVENT = "qrToken";
  private static final String PING_EVENT = "ping";
  private static final String CHANNEL_PREFIX = "ATTENDANCE_QR:";
  private static final long WINDOW_SECONDS = RollingQrTokenUtil.DEFAULT_WINDOW_SECONDS;
  private static final long PING_SECONDS = 15;

  private final AttendanceRoundRepository attendanceRoundRepository;
  private final AttendanceAuthorizationService authorizationService;
  private final SseService sseService;

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
  private final ConcurrentHashMap<UUID, ScheduledFuture<?>> tokenTaskByRound = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, ScheduledFuture<?>> pingTaskByRound = new ConcurrentHashMap<>();

  /**
   * SSE 구독 (관리자/OWNER) - round가 ACTIVE가 아니면 구독 불가.
   */
  public SseEmitter subscribe(UUID roundId, UUID userId) {
    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId)
        .orElseThrow(() -> new CustomException(ErrorCode.ROUND_NOT_FOUND));

    UUID sessionId = round.getAttendanceSession().getAttendanceSessionId();
    authorizationService.ensureAdmin(sessionId, userId);

    if (round.getRoundStatus() != RoundStatus.ACTIVE) {
      throw new CustomException(ErrorCode.ROUND_NOT_ACTIVE);
    }

    String channelId = channelId(roundId);
    SseEmitter emitter = sseService.subscribe(channelId);

    try {
      emitter.send(SseEmitter.event()
          .name(QR_TOKEN_EVENT)
          .data(createPayload(round), MediaType.APPLICATION_JSON));
    } catch (IOException e) {
      sseService.removeEmitter(channelId, emitter);
      throw new IllegalStateException("SSE_SEND_FAILED", e);
    }

    startRoundTasksIfAbsent(roundId);
    return emitter;
  }

  public String createQrUrl(UUID roundId, String token) {
    return String.format("%s%s?roundId=%s&token=%s", frontendUrl, ATTENDANCE_PATH, roundId, token);
  }

  private void startRoundTasksIfAbsent(UUID roundId) {
    tokenTaskByRound.computeIfAbsent(roundId, rid -> {
      long now = Instant.now().getEpochSecond();
      long boundary = RollingQrTokenUtil.toWindowExpiresAt(now, WINDOW_SECONDS);
      long initialDelay = Math.max(0, boundary - now);

      log.info("SSE token broadcaster scheduled: roundId={}, initialDelaySec={}, periodSec={}",
          rid, initialDelay, WINDOW_SECONDS);

      return scheduler.scheduleAtFixedRate(() -> {
        try {
          broadcastNewToken(rid);
        } catch (Exception ex) {
          log.error("Token broadcast failed: roundId={}", rid, ex);
        }
      }, initialDelay, WINDOW_SECONDS, TimeUnit.SECONDS);
    });

    pingTaskByRound.computeIfAbsent(roundId, rid -> scheduler.scheduleAtFixedRate(() -> {
      try {
        String channelId = channelId(rid);
        if (!sseService.hasSubscribers(channelId)) {
          stopRoundTasksIfNoSubscribers(rid);
          return;
        }

        sseService.send(channelId, PING_EVENT, "ok");
      } catch (Exception ex) {
        log.error("Ping task failed: roundId={}", rid, ex);
      }
    }, PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS));
  }

  private void broadcastNewToken(UUID roundId) {
    String channelId = channelId(roundId);
    if (!sseService.hasSubscribers(channelId)) {
      stopRoundTasksIfNoSubscribers(roundId);
      return;
    }

    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId).orElse(null);
    if (round == null || round.getRoundStatus() != RoundStatus.ACTIVE) {
      completeChannel(roundId);
      stopRoundTasksIfNoSubscribers(roundId);
      return;
    }

    sseService.send(channelId, QR_TOKEN_EVENT, createPayload(round));
  }

  private AttendanceRoundQrTokenResponse createPayload(AttendanceRound round) {
    RollingQrTokenUtil.IssuedToken issued = RollingQrTokenUtil.issue(round.getRoundId(), round.getQrSecret());
    String qrUrl = createQrUrl(round.getRoundId(), issued.token());
    return new AttendanceRoundQrTokenResponse(round.getRoundId(), qrUrl, issued.expiresAtEpochSec());
  }

  private void completeChannel(UUID roundId) {
    sseService.complete(channelId(roundId));
  }

  private void stopRoundTasksIfNoSubscribers(UUID roundId) {
    if (sseService.hasSubscribers(channelId(roundId))) {
      return;
    }

    ScheduledFuture<?> tokenFuture = tokenTaskByRound.remove(roundId);
    if (tokenFuture != null) {
      tokenFuture.cancel(false);
    }

    ScheduledFuture<?> pingFuture = pingTaskByRound.remove(roundId);
    if (pingFuture != null) {
      pingFuture.cancel(false);
    }
  }

  private String channelId(UUID roundId) {
    return CHANNEL_PREFIX + roundId;
  }

  @PreDestroy
  public void cleanup() {
    tokenTaskByRound.keySet().forEach(this::completeChannel);

    tokenTaskByRound.values().forEach(future -> future.cancel(false));
    pingTaskByRound.values().forEach(future -> future.cancel(false));
    tokenTaskByRound.clear();
    pingTaskByRound.clear();

    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
