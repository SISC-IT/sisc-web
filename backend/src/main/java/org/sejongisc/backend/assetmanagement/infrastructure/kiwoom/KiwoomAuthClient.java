package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.KiwoomTokenRequest;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.KiwoomTokenResponse;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
public class KiwoomAuthClient {
  private static final DateTimeFormatter KIWOOM_EXPIRES_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final WebClient kiwoomWebClient;
  private final KiwoomRateLimiter rateLimiter;
  private final String appKey;
  private final String appSecret;
  private final Duration blockTimeout;
  private final ZoneId tokenExpiryZoneId;

  public KiwoomAuthClient(
      @Qualifier("kiwoomWebClient") WebClient kiwoomWebClient,
      KiwoomRateLimiter rateLimiter,
      @Value("${kiwoom.api.app-key:}") String appKey,
      @Value("${kiwoom.api.app-secret:}") String appSecret,
      @Value("${kiwoom.api.timeout.block-ms:7000}") long blockTimeoutMillis,
      @Value("${kiwoom.api.token-expiry-zone:Asia/Seoul}") String tokenExpiryZoneId
  ) {
    this.kiwoomWebClient = kiwoomWebClient;
    this.rateLimiter = rateLimiter;
    this.appKey = appKey;
    this.appSecret = appSecret;
    this.blockTimeout = Duration.ofMillis(Math.max(1000, blockTimeoutMillis));
    this.tokenExpiryZoneId = ZoneId.of(tokenExpiryZoneId);
  }

  public KiwoomAccessToken issueAccessToken() {
    if (!StringUtils.hasText(appKey) || !StringUtils.hasText(appSecret)) {
      log.error("[KiwoomAuth] app key/secret 환경변수가 설정되지 않았습니다.");
      throw new CustomException(ErrorCode.KIWOOM_AUTH_FAILED);
    }

    KiwoomTokenRequest request = KiwoomTokenRequest.of(appKey, appSecret);

    try {
      KiwoomTokenResponse response = rateLimiter.call(() -> kiwoomWebClient.post()
          .uri("/oauth2/token")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .retrieve()
          .bodyToMono(KiwoomTokenResponse.class)
          .block(blockTimeout));

      return parseToken(response);
    } catch (WebClientResponseException e) {
      log.warn("[KiwoomAuth] 토큰 발급 HTTP 실패: status={}", e.getStatusCode());
      throw new CustomException(ErrorCode.KIWOOM_AUTH_FAILED);
    } catch (WebClientRequestException | IllegalStateException e) {
      log.warn("[KiwoomAuth] 토큰 발급 요청 실패: {}", e.getMessage());
      throw new CustomException(ErrorCode.KIWOOM_AUTH_FAILED);
    }
  }

  private KiwoomAccessToken parseToken(KiwoomTokenResponse response) {
    if (response == null || !StringUtils.hasText(response.getToken())) {
      log.warn("[KiwoomAuth] 토큰 발급 응답이 비어 있습니다.");
      throw new CustomException(ErrorCode.KIWOOM_AUTH_FAILED);
    }

    if (isFailureCode(response.getReturnCode())) {
      log.warn("[KiwoomAuth] 토큰 발급 거절: returnCode={}, returnMsg={}",
          response.getReturnCode(), response.getReturnMsg());
      throw new CustomException(ErrorCode.KIWOOM_AUTH_FAILED);
    }

    Instant expiresAt = parseExpiresAt(response.getExpiresDt());
    log.info("[KiwoomAuth] 토큰 발급 성공: expiresAt={}", expiresAt);
    return new KiwoomAccessToken(response.getToken(), expiresAt);
  }

  private boolean isFailureCode(String returnCode) {
    return StringUtils.hasText(returnCode) && !"0".equals(returnCode.trim());
  }

  private Instant parseExpiresAt(String expiresDt) {
    if (!StringUtils.hasText(expiresDt)) {
      log.warn("[KiwoomAuth] 토큰 만료시각이 없어 보수적인 TTL을 적용합니다.");
      return Instant.now().plus(Duration.ofMinutes(30));
    }

    String value = expiresDt.trim();

    try {
      return LocalDateTime.parse(value, KIWOOM_EXPIRES_FORMATTER)
          .atZone(tokenExpiryZoneId)
          .toInstant();
    } catch (RuntimeException ignored) {
      // Try ISO variants below.
    }

    try {
      return Instant.parse(value);
    } catch (RuntimeException ignored) {
      // Try offset date-time below.
    }

    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (RuntimeException ignored) {
      log.warn("[KiwoomAuth] 토큰 만료시각 파싱 실패: expiresDt={}", value);
      return Instant.now().plus(Duration.ofMinutes(30));
    }
  }
}
