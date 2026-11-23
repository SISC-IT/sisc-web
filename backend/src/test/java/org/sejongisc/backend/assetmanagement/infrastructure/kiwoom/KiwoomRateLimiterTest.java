package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KiwoomRateLimiterTest {
  private static final String RESTRICTED_API_ID = "kt00004";
  private static final String CHART_API_ID = "ka10081";
  private static final String US_CHART_API_ID = "usa06012";

  private long nowNanos;
  private List<Long> sleptMillis;
  private KiwoomRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    nowNanos = TimeUnit.SECONDS.toNanos(10);
    sleptMillis = new ArrayList<>();
    rateLimiter = new KiwoomRateLimiter(
        3,
        100,
        1000,
        Set.of(RESTRICTED_API_ID),
        () -> nowNanos,
        nanos -> {
          sleptMillis.add(TimeUnit.NANOSECONDS.toMillis(nanos));
          nowNanos += nanos;
        }
    );
  }

  @Test
  @DisplayName("제한 대상 TR은 연속 호출 시 1초 간격을 둔다")
  void restrictedApiWaitsRestrictedInterval() {
    rateLimiter.call(RESTRICTED_API_ID, () -> "first");
    rateLimiter.call(RESTRICTED_API_ID, () -> "second");
    rateLimiter.call(RESTRICTED_API_ID, () -> "third");

    assertThat(sleptMillis).containsExactly(1000L, 1000L);
  }

  @Test
  @DisplayName("제한 대상이 아닌 API는 기존 간격을 유지한다")
  void unrestrictedApiKeepsDefaultInterval() {
    rateLimiter.call(CHART_API_ID, () -> "first");
    rateLimiter.call(CHART_API_ID, () -> "second");

    assertThat(sleptMillis).containsExactly(100L);
  }

  @Test
  @DisplayName("API ID가 달라도 전역 간격은 함께 지킨다")
  void differentApisShareGlobalInterval() {
    rateLimiter.call(RESTRICTED_API_ID, () -> "account");
    rateLimiter.call(CHART_API_ID, () -> "chart");
    rateLimiter.call(US_CHART_API_ID, () -> "us-chart");

    assertThat(sleptMillis).containsExactly(100L, 100L);
  }

  @Test
  @DisplayName("제한 대상 TR은 사이에 다른 API가 끼어도 직전 호출로부터 1초를 채운다")
  void restrictedApiKeepsIntervalAcrossOtherCalls() {
    rateLimiter.call(RESTRICTED_API_ID, () -> "first");
    rateLimiter.call(CHART_API_ID, () -> "chart");
    rateLimiter.call(RESTRICTED_API_ID, () -> "second");

    assertThat(sleptMillis).containsExactly(100L, 900L);
  }

  @Test
  @DisplayName("전역 대기로 늦게 나간 제한 대상 TR은 실제 나간 시각부터 1초를 센다")
  void restrictedIntervalCountsFromActualSendTime() {
    rateLimiter.call(CHART_API_ID, () -> "chart");
    rateLimiter.call(RESTRICTED_API_ID, () -> "first");
    rateLimiter.call(RESTRICTED_API_ID, () -> "second");

    assertThat(sleptMillis).containsExactly(100L, 1000L);
  }

  @Test
  @DisplayName("간격 대기 중인 제한 대상 TR은 슬롯을 잡지 않아 다른 API를 막지 않는다")
  void waitingRestrictedApiDoesNotHoldPermit() throws Exception {
    CountDownLatch waitingStarted = new CountDownLatch(1);
    CountDownLatch releaseWaiting = new CountDownLatch(1);
    KiwoomRateLimiter singlePermitLimiter = new KiwoomRateLimiter(
        1,
        0,
        1000,
        Set.of(RESTRICTED_API_ID),
        () -> 0L,
        nanos -> {
          waitingStarted.countDown();
          releaseWaiting.await();
        }
    );
    singlePermitLimiter.call(RESTRICTED_API_ID, () -> "first");

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<String> waiting =
          executor.submit(() -> singlePermitLimiter.call(RESTRICTED_API_ID, () -> "second"));
      assertThat(waitingStarted.await(5, TimeUnit.SECONDS)).isTrue();

      Future<String> chart =
          executor.submit(() -> singlePermitLimiter.call(CHART_API_ID, () -> "chart"));

      assertThat(chart.get(1, TimeUnit.SECONDS)).isEqualTo("chart");
      assertThat(waiting.isDone()).isFalse();

      releaseWaiting.countDown();
      assertThat(waiting.get(5, TimeUnit.SECONDS)).isEqualTo("second");
    } finally {
      releaseWaiting.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("간격 일부가 이미 지났으면 남은 시간만 기다린다")
  void waitsOnlyRemainingInterval() {
    rateLimiter.call(RESTRICTED_API_ID, () -> "first");
    nowNanos += TimeUnit.MILLISECONDS.toNanos(400);
    rateLimiter.call(RESTRICTED_API_ID, () -> "second");

    assertThat(sleptMillis).containsExactly(600L);
  }

  @Test
  @DisplayName("간격이 이미 지났으면 기다리지 않는다")
  void doesNotWaitAfterIntervalElapsed() {
    rateLimiter.call(RESTRICTED_API_ID, () -> "first");
    nowNanos += TimeUnit.MILLISECONDS.toNanos(1000);
    rateLimiter.call(RESTRICTED_API_ID, () -> "second");

    assertThat(sleptMillis).isEmpty();
  }

  @Test
  @DisplayName("호출 결과를 그대로 반환한다")
  void returnsSupplierResult() {
    assertThat(rateLimiter.call(CHART_API_ID, () -> "result")).isEqualTo("result");
  }
}
