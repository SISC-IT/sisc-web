package org.sejongisc.backend.attendance.service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.core.env.Environment;

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

  @Value("${app.prod-frontend-url}")
  private String prodFrontendUrl;

  @Value("${app.dev-frontend-url}")
  private String devFrontendUrl;

  private final Environment environment;

  private static final String ATTENDANCE_PATH = "/attendance";

  private static final long WINDOW_SECONDS = RollingQrTokenUtil.DEFAULT_WINDOW_SECONDS;
  private static final long PING_SECONDS = 15;

  private final AttendanceRoundRepository attendanceRoundRepository;
  private final AttendanceAuthorizationService authorizationService;

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

  private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByRound = new ConcurrentHashMap<>();
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

    // timeout 0 = 무제한 (서버/프록시 설정에 따라 끊길 수 있으니 ping으로 유지)
    SseEmitter emitter = new SseEmitter(0L);

    emittersByRound.computeIfAbsent(roundId, k -> new CopyOnWriteArrayList<>()).add(emitter);

    // 연결 종료 시 정리
    emitter.onCompletion(() -> removeEmitter(roundId, emitter));
    emitter.onTimeout(() -> removeEmitter(roundId, emitter));
    emitter.onError((ex) -> removeEmitter(roundId, emitter));

    // 즉시 현재 토큰 push
    try {
      RollingQrTokenUtil.IssuedToken issued = RollingQrTokenUtil.issue(roundId, round.getQrSecret());
      String qrUrl = createQrUrl(roundId, issued.token());

      AttendanceRoundQrTokenResponse payload =
          new AttendanceRoundQrTokenResponse(roundId, qrUrl, issued.expiresAtEpochSec());

      emitter.send(SseEmitter.event()
          .name("qrToken")
          .data(payload, MediaType.APPLICATION_JSON));
    } catch (IOException e) {
      removeEmitter(roundId, emitter);
      throw new IllegalStateException("SSE_SEND_FAILED", e);
    }

    // round별 토큰 브로드캐스트 작업/핑 작업이 없으면 시작
    startRoundTasksIfAbsent(roundId);

    return emitter;
  }

  public String createQrUrl(UUID roundId, String token) {
    String baseUrl = devFrontendUrl;

    // 활성화된 프로필 중 prod가 있으면 운영 서버 주소 사용
    for (String profile : environment.getActiveProfiles()) {
      if ("prod".equalsIgnoreCase(profile)) {
        baseUrl = prodFrontendUrl;
        break;
      }
    }

    return String.format("%s%s?roundId=%s&token=%s", baseUrl, ATTENDANCE_PATH, roundId, token);
  }

  private void startRoundTasksIfAbsent(UUID roundId) {
    tokenTaskByRound.computeIfAbsent(roundId, rid -> {
      long now = Instant.now().getEpochSecond();
      long boundary = RollingQrTokenUtil.toWindowExpiresAt(now, WINDOW_SECONDS);
      long initialDelay = Math.max(0, boundary - now); // 경계 시점에 실행

      log.info("SSE token broadcaster scheduled: roundId={}, initialDelaySec={}, periodSec={}",
          rid, initialDelay, WINDOW_SECONDS);

      // scheduleAtFixedRate 내부 예외 발생 시 태스크가 영구 중단될 수 있으니 최상위 try-catch로 보호
      return scheduler.scheduleAtFixedRate(() -> {
        try {
          broadcastNewToken(rid);
        } catch (Exception ex) {
          log.error("Token broadcast failed: roundId={}", rid, ex);
        }
      }, initialDelay, WINDOW_SECONDS, TimeUnit.SECONDS);
    });

    pingTaskByRound.computeIfAbsent(roundId, rid -> scheduler.scheduleAtFixedRate(() -> {
      // ping 태스크도 최상위 try-catch로 보호
      try {
        var emitters = emittersByRound.get(rid);
        if (emitters == null || emitters.isEmpty()) {
          stopRoundTasksIfNoEmitters(rid);
          return;
        }
        for (SseEmitter emitter : emitters) {
          try {
            emitter.send(SseEmitter.event().name("ping").data("ok"));
          } catch (Exception e) {
            removeEmitter(rid, emitter);
          }
        }
      } catch (Exception ex) {
        log.error("Ping task failed: roundId={}", rid, ex);
      }
    }, PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS));
  }

  private void broadcastNewToken(UUID roundId) {
    CopyOnWriteArrayList<SseEmitter> emitters = emittersByRound.get(roundId);
    if (emitters == null || emitters.isEmpty()) {
      stopRoundTasksIfNoEmitters(roundId);
      return;
    }

    AttendanceRound round = attendanceRoundRepository.findRoundById(roundId).orElse(null);
    if (round == null) {
      completeAll(roundId);
      stopRoundTasksIfNoEmitters(roundId);
      return;
    }

    if (round.getRoundStatus() != RoundStatus.ACTIVE) {
      // 라운드가 닫혔으면 스트림 종료
      completeAll(roundId);
      stopRoundTasksIfNoEmitters(roundId);
      return;
    }

    RollingQrTokenUtil.IssuedToken issued = RollingQrTokenUtil.issue(roundId, round.getQrSecret());
    String qrUrl = createQrUrl(roundId, issued.token());

    AttendanceRoundQrTokenResponse payload =
        new AttendanceRoundQrTokenResponse(roundId, qrUrl, issued.expiresAtEpochSec());

    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event()
            .name("qrToken")
            .data(payload, MediaType.APPLICATION_JSON));
      } catch (Exception e) {
        removeEmitter(roundId, emitter);
      }
    }
  }

  private void removeEmitter(UUID roundId, SseEmitter emitter) {
    emittersByRound.computeIfPresent(roundId, (key, emitters) -> {
      emitters.remove(emitter);
      return emitters.isEmpty() ? null : emitters;
    });

    stopRoundTasksIfNoEmitters(roundId);
  }

  private void completeAll(UUID roundId) {
    List<SseEmitter> removed = emittersByRound.remove(roundId);
    if (removed == null) return;

    for (SseEmitter e : removed) {
      try {
        e.complete();
      } catch (Exception ignore) {
      }
    }
  }

  private void stopRoundTasksIfNoEmitters(UUID roundId) {
    CopyOnWriteArrayList<SseEmitter> emitters = emittersByRound.get(roundId);
    if (emitters != null && !emitters.isEmpty()) return;

    ScheduledFuture<?> tokenFuture = tokenTaskByRound.remove(roundId);
    if (tokenFuture != null) tokenFuture.cancel(false);

    ScheduledFuture<?> pingFuture = pingTaskByRound.remove(roundId);
    if (pingFuture != null) pingFuture.cancel(false);
  }

  @PreDestroy
  public void cleanup() {
    // SSE 연결 종료
    emittersByRound.forEach((rid, list) -> {
      for (SseEmitter e : list) {
        try {
          e.complete();
        } catch (Exception ignore) {
        }
      }
    });
    emittersByRound.clear();

    // 예약 작업 취소
    tokenTaskByRound.values().forEach(f -> f.cancel(false));
    pingTaskByRound.values().forEach(f -> f.cancel(false));
    tokenTaskByRound.clear();
    pingTaskByRound.clear();

    // 스레드풀 종료
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
