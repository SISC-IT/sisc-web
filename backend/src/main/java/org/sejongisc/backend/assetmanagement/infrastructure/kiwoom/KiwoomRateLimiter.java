package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KiwoomRateLimiter {
  private final Semaphore semaphore;
  private final long minIntervalMillis;
  private final Object throttleMonitor = new Object();
  private long lastRequestStartedAt = 0L;

  public KiwoomRateLimiter(
      @Value("${kiwoom.api.rate-limit.max-concurrent:3}") int maxConcurrent,
      @Value("${kiwoom.api.rate-limit.min-interval-ms:100}") long minIntervalMillis
  ) {
    this.semaphore = new Semaphore(Math.max(1, maxConcurrent), true);
    this.minIntervalMillis = Math.max(0, minIntervalMillis);
  }

  public <T> T call(Supplier<T> supplier) {
    boolean acquired = false;
    try {
      semaphore.acquire();
      acquired = true;
      throttle();
      return supplier.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CustomException(ErrorCode.KIWOOM_API_FAILED);
    } finally {
      if (acquired) {
        semaphore.release();
      }
    }
  }

  private void throttle() throws InterruptedException {
    if (minIntervalMillis <= 0) {
      return;
    }

    synchronized (throttleMonitor) {
      long now = System.currentTimeMillis();
      long elapsed = now - lastRequestStartedAt;
      long waitMillis = minIntervalMillis - elapsed;

      if (waitMillis > 0) {
        Thread.sleep(waitMillis);
      }

      lastRequestStartedAt = System.currentTimeMillis();
    }
  }
}
