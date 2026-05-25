package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KiwoomTokenManager {
  private final KiwoomAuthClient kiwoomAuthClient;
  private final Duration refreshSkew;
  private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();
  private final ReentrantLock refreshLock = new ReentrantLock();

  public KiwoomTokenManager(
      KiwoomAuthClient kiwoomAuthClient,
      @Value("${kiwoom.api.token-refresh-skew-seconds:300}") long refreshSkewSeconds
  ) {
    this.kiwoomAuthClient = kiwoomAuthClient;
    this.refreshSkew = Duration.ofSeconds(Math.max(30, refreshSkewSeconds));
  }

  public String getValidToken() {
    CachedToken current = cachedToken.get();
    if (isUsable(current)) {
      return current.value();
    }

    refreshLock.lock();
    try {
      current = cachedToken.get();
      if (isUsable(current)) {
        return current.value();
      }

      KiwoomAccessToken issuedToken = kiwoomAuthClient.issueAccessToken();
      CachedToken updatedToken = new CachedToken(issuedToken.value(), issuedToken.expiresAt());
      cachedToken.set(updatedToken);
      log.info("[KiwoomToken] 토큰 캐시 갱신 완료: expiresAt={}", updatedToken.expiresAt());
      return updatedToken.value();
    } finally {
      refreshLock.unlock();
    }
  }

  public void invalidateIfCurrent(String tokenValue) {
    if (tokenValue == null) {
      return;
    }

    cachedToken.updateAndGet(current -> {
      if (current != null && Objects.equals(current.value(), tokenValue)) {
        log.info("[KiwoomToken] 인증 실패 토큰 캐시 무효화");
        return null;
      }
      return current;
    });
  }

  private boolean isUsable(CachedToken token) {
    return token != null && Instant.now().plus(refreshSkew).isBefore(token.expiresAt());
  }

  private record CachedToken(String value, Instant expiresAt) {
  }
}
