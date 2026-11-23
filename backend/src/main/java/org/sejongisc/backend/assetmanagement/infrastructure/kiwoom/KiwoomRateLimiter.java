package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KiwoomRateLimiter {
  private final Semaphore semaphore;
  private final long minIntervalNanos;
  private final long restrictedMinIntervalNanos;
  private final Set<String> restrictedApiIds;
  private final LongSupplier nanoClock;
  private final Sleeper sleeper;

  private final Object globalMonitor = new Object();
  private Long nextGlobalSlot;
  private final Map<String, RestrictedGate> restrictedGates = new ConcurrentHashMap<>();

  @Autowired
  public KiwoomRateLimiter(
      @Value("${kiwoom.api.rate-limit.max-concurrent:3}") int maxConcurrent,
      @Value("${kiwoom.api.rate-limit.min-interval-ms:200}") long minIntervalMillis,
      @Value("${kiwoom.api.rate-limit.restricted.min-interval-ms:1000}")
      long restrictedMinIntervalMillis,
      @Value("${kiwoom.api.rate-limit.restricted.api-ids:}") Set<String> restrictedApiIds
  ) {
    this(
        maxConcurrent,
        minIntervalMillis,
        restrictedMinIntervalMillis,
        restrictedApiIds,
        System::nanoTime,
        TimeUnit.NANOSECONDS::sleep
    );
  }

  KiwoomRateLimiter(
      int maxConcurrent,
      long minIntervalMillis,
      long restrictedMinIntervalMillis,
      Set<String> restrictedApiIds,
      LongSupplier nanoClock,
      Sleeper sleeper
  ) {
    this.semaphore = new Semaphore(Math.max(1, maxConcurrent), true);
    this.minIntervalNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, minIntervalMillis));
    this.restrictedMinIntervalNanos =
        TimeUnit.MILLISECONDS.toNanos(Math.max(0, restrictedMinIntervalMillis));
    this.restrictedApiIds = restrictedApiIds == null ? Set.of() : Set.copyOf(restrictedApiIds);
    this.nanoClock = nanoClock;
    this.sleeper = sleeper;
  }

  public <T> T call(String apiId, Supplier<T> supplier) {
    try {
      long slot = restrictedApiIds.contains(apiId) ? acquireRestricted(apiId) : acquire();
      try {
        sleepUntil(slot);
        return supplier.get();
      } finally {
        semaphore.release();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CustomException(ErrorCode.KIWOOM_API_FAILED);
    }
  }

  private long acquire() throws InterruptedException {
    semaphore.acquire();
    return reserveGlobalSlot();
  }

  private long acquireRestricted(String apiId) throws InterruptedException {
    RestrictedGate gate = restrictedGates.computeIfAbsent(apiId, id -> new RestrictedGate());
    gate.lock.lockInterruptibly();
    try {
      if (gate.nextSlot != null) {
        sleepUntil(gate.nextSlot);
      }
      long slot = acquire();
      gate.nextSlot = slot + restrictedMinIntervalNanos;
      return slot;
    } finally {
      gate.lock.unlock();
    }
  }

  private long reserveGlobalSlot() {
    synchronized (globalMonitor) {
      long now = nanoClock.getAsLong();
      long slot = nextGlobalSlot == null ? now : Math.max(now, nextGlobalSlot);
      nextGlobalSlot = slot + minIntervalNanos;
      return slot;
    }
  }

  private void sleepUntil(long targetNanos) throws InterruptedException {
    long waitNanos = targetNanos - nanoClock.getAsLong();
    if (waitNanos > 0) {
      sleeper.sleep(waitNanos);
    }
  }

  private static final class RestrictedGate {
    private final ReentrantLock lock = new ReentrantLock(true);
    private Long nextSlot;
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(long nanos) throws InterruptedException;
  }
}
