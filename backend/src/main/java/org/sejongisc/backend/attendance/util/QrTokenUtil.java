package org.sejongisc.backend.attendance.util;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class QrTokenUtil {
  private static final SecureRandom RNG = new SecureRandom();
  private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

  private QrTokenUtil() {}

  /** 라운드별 비밀키(절대 노출 X) 생성 */
  public static String generateSecret() {
    byte[] buf = new byte[32]; // 256-bit
    RNG.nextBytes(buf);
    return B64URL.encodeToString(buf);
  }

  /** qrToken 발급 */
  public static IssuedToken issue(UUID roundId, String qrSecret, long ttlSeconds) {
    long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
    String data = roundId + ":" + expiresAt;
    String sig = sign(data, qrSecret);
    return new IssuedToken(data + ":" + sig, expiresAt);
  }

  /** qrToken 검증 + 파싱(서명/만료만 검사) */
  public static ParsedToken verifyAndParse(String token, String qrSecret) {
    if (token == null || token.isBlank()) throw new IllegalStateException("QR_TOKEN_REQUIRED");

    String[] parts = token.split(":");
    if (parts.length != 3) throw new IllegalStateException("QR_TOKEN_MALFORMED");

    UUID roundId;
    long expiresAt;
    try {
      roundId = UUID.fromString(parts[0]);
      expiresAt = Long.parseLong(parts[1]);
    } catch (Exception e) {
      throw new IllegalStateException("QR_TOKEN_MALFORMED");
    }

    long now = Instant.now().getEpochSecond();
    if (now > expiresAt) throw new IllegalStateException("QR_TOKEN_EXPIRED");

    String data = parts[0] + ":" + parts[1];
    String expectedSig = sign(data, qrSecret);

    if (!constantTimeEquals(expectedSig, parts[2])) {
      throw new IllegalStateException("QR_TOKEN_INVALID");
    }

    return new ParsedToken(roundId, expiresAt);
  }

  private static String sign(String data, String qrSecretB64Url) {
    try {
      byte[] key = Base64.getUrlDecoder().decode(qrSecretB64Url);
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return B64URL.encodeToString(raw);
    } catch (Exception e) {
      throw new IllegalStateException("QR_SIGN_ERROR");
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8),
        b.getBytes(StandardCharsets.UTF_8)
    );
  }

  public record IssuedToken(String token, long expiresAtEpochSec) {}
  public record ParsedToken(UUID roundId, long expiresAtEpochSec) {}
}

