package org.sejongisc.backend.attendance.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 3분 단위(기본 180초)로 회전하는 QR 토큰 유틸.
 *
 * 검증 시에는 "현재 시각이 해당 윈도우 범위 안"(windowStart <= now < expiresAt)을 만족해야 유효.
 */
public final class RollingQrTokenUtil {

  private RollingQrTokenUtil() {}

  public static final long DEFAULT_WINDOW_SECONDS = 180; // 3분

  // 모바일/서버 시계 오차, 네트워크 지연 약간 허용
  private static final long SKEW_SECONDS = 5;

  public record IssuedToken(String token, long expiresAtEpochSec) {}

  public record ParsedToken(UUID roundId, long expiresAtEpochSec) {}

  public static IssuedToken issue(UUID roundId, String secret) {
    return issue(roundId, secret, DEFAULT_WINDOW_SECONDS);
  }

  public static IssuedToken issue(UUID roundId, String secret, long windowSeconds) {
    long now = Instant.now().getEpochSecond();
    long expiresAt = toWindowExpiresAt(now, windowSeconds);
    String token = buildToken(roundId, secret, expiresAt);
    return new IssuedToken(token, expiresAt);
  }

  public static ParsedToken verifyAndParse(String token, String secret) {
    return verifyAndParse(token, secret, DEFAULT_WINDOW_SECONDS);
  }

  public static ParsedToken verifyAndParse(String token, String secret, long windowSeconds) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("QR_TOKEN_MALFORMED");
    }

    String[] parts = token.split(":");
    if (parts.length != 3) {
      throw new IllegalArgumentException("QR_TOKEN_MALFORMED");
    }

    UUID roundId;
    long expiresAt;
    try {
      roundId = UUID.fromString(parts[0]);
      expiresAt = Long.parseLong(parts[1]);
    } catch (Exception e) {
      throw new IllegalArgumentException("QR_TOKEN_MALFORMED");
    }

    String payload = roundId + ":" + expiresAt;
    String expectedSig = hmacSha256Base64Url(payload, secret);

    // constant-time compare
    if (!constantTimeEquals(expectedSig, parts[2])) {
      throw new IllegalArgumentException("QR_TOKEN_MALFORMED");
    }

    long now = Instant.now().getEpochSecond();
    long windowStart = expiresAt - windowSeconds;

    // 유효 범위 체크(스큐 허용)
    if (now < windowStart - SKEW_SECONDS || now >= expiresAt + SKEW_SECONDS) {
      throw new IllegalArgumentException("QR_TOKEN_MALFORMED");
    }

    return new ParsedToken(roundId, expiresAt);
  }

  /** 현재 윈도우의 끝(expiresAt) 계산 */
  public static long toWindowExpiresAt(long nowEpochSec, long windowSeconds) {
    long windowStart = (nowEpochSec / windowSeconds) * windowSeconds;
    return windowStart + windowSeconds;
  }

  /** 특정 expiresAt 윈도우 토큰 생성 */
  public static String buildToken(UUID roundId, String secret, long expiresAtEpochSec) {
    String payload = roundId + ":" + expiresAtEpochSec;
    String sig = hmacSha256Base64Url(payload, secret);
    return payload + ":" + sig;
  }

  private static String hmacSha256Base64Url(String data, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    } catch (Exception e) {
      throw new IllegalStateException("QR_TOKEN_MALFORMED", e);
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    if (a.length() != b.length()) return false;
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }
}
