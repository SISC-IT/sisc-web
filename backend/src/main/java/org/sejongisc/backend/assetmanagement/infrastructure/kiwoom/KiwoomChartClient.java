package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.DomesticDailyChartRequest;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.DomesticDailyChartResponse;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.KiwoomResponse;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.UsDailyChartRequest;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.UsDailyChartResponse;
import org.sejongisc.backend.assetmanagement.model.DailyCandle;
import org.sejongisc.backend.assetmanagement.model.UsExchangeType;
import org.sejongisc.backend.assetmanagement.service.MarketDataClient;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 키움 차트 API 클라이언트. 국내 일봉(ka10081)과 미국 일봉(usa06012)을 조회한다.
 *
 * <p>토큰 관리와 레이트 리밋은 계좌 조회와 동일한 빈을 공유하므로, 차트 조회가 늘어나도
 * 키움 전체 호출량이 설정된 한도 안에서 유지된다.
 */
@Slf4j
@Component
public class KiwoomChartClient implements MarketDataClient {
  private static final String DOMESTIC_CHART_ENDPOINT = "/api/dostk/chart";
  private static final String US_CHART_ENDPOINT = "/api/us/chart";
  private static final String DOMESTIC_DAILY_CHART_API_ID = "ka10081";
  private static final String US_DAILY_CHART_API_ID = "usa06012";
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final String CURRENCY_KRW = "KRW";
  private static final String CURRENCY_USD = "USD";

  private final WebClient kiwoomWebClient;
  private final KiwoomTokenManager tokenManager;
  private final KiwoomRateLimiter rateLimiter;
  private final int maxPages;
  private final Duration blockTimeout;

  public KiwoomChartClient(
      @Qualifier("kiwoomWebClient") WebClient kiwoomWebClient,
      KiwoomTokenManager tokenManager,
      KiwoomRateLimiter rateLimiter,
      @Value("${kiwoom.api.pagination.max-pages:20}") int maxPages,
      @Value("${kiwoom.api.timeout.block-ms:7000}") long blockTimeoutMillis
  ) {
    this.kiwoomWebClient = kiwoomWebClient;
    this.tokenManager = tokenManager;
    this.rateLimiter = rateLimiter;
    this.maxPages = Math.max(1, maxPages);
    this.blockTimeout = Duration.ofMillis(Math.max(1000, blockTimeoutMillis));
  }

  @Override
  public List<DailyCandle> fetchDomesticDailyCandles(String stockCode, LocalDate baseDate) {
    validateStockCode(stockCode);
    validateDate(baseDate);

    List<DomesticDailyChartResponse> pages = executePagingTemplate(
        DOMESTIC_CHART_ENDPOINT,
        DOMESTIC_DAILY_CHART_API_ID,
        DomesticDailyChartRequest.of(stockCode, baseDate.format(DATE_FORMAT)),
        DomesticDailyChartResponse.class
    );

    return pages.stream()
        .filter(page -> page.getCandles() != null)
        .flatMap(page -> page.getCandles().stream())
        .map(candle -> toDailyCandle(
            stockCode,
            CURRENCY_KRW,
            candle.getDate(),
            candle.getClosePrice(),
            candle.getOpenPrice(),
            candle.getHighPrice(),
            candle.getLowPrice(),
            candle.getTradeVolume()
        ))
        .sorted(Comparator.comparing(DailyCandle::date))
        .toList();
  }

  @Override
  public List<DailyCandle> fetchUsDailyCandles(
      UsExchangeType exchangeType, String stockCode, LocalDate startDate) {
    if (exchangeType == null) {
      throw new CustomException(ErrorCode.INVALID_KIWOOM_US_EXCHANGE_TYPE);
    }
    validateStockCode(stockCode);
    validateDate(startDate);

    List<UsDailyChartResponse> pages = executePagingTemplate(
        US_CHART_ENDPOINT,
        US_DAILY_CHART_API_ID,
        UsDailyChartRequest.of(
            exchangeType.code(), stockCode, startDate.format(DATE_FORMAT)),
        UsDailyChartResponse.class
    );

    return pages.stream()
        .filter(page -> page.getCandles() != null)
        .flatMap(page -> page.getCandles().stream())
        .map(candle -> toDailyCandle(
            stockCode,
            CURRENCY_USD,
            candle.getDate(),
            candle.getClosePrice(),
            candle.getOpenPrice(),
            candle.getHighPrice(),
            candle.getLowPrice(),
            candle.getAccumulatedVolume()
        ))
        .sorted(Comparator.comparing(DailyCandle::date))
        .toList();
  }

  private void validateStockCode(String stockCode) {
    if (!StringUtils.hasText(stockCode)) {
      throw new CustomException(ErrorCode.INVALID_KIWOOM_STOCK_CODE);
    }
  }

  private void validateDate(LocalDate date) {
    if (date == null) {
      throw new CustomException(ErrorCode.INVALID_KIWOOM_QUERY_DATE);
    }
  }

  private DailyCandle toDailyCandle(
      String ticker,
      String currency,
      String date,
      String closePrice,
      String openPrice,
      String highPrice,
      String lowPrice,
      String volume
  ) {
    return DailyCandle.builder()
        .ticker(ticker)
        .date(parseDate(date))
        .closePrice(parsePrice(closePrice))
        .openPrice(parsePrice(openPrice))
        .highPrice(parsePrice(highPrice))
        .lowPrice(parsePrice(lowPrice))
        .volume(parseVolume(volume))
        .currency(currency)
        .build();
  }

  private LocalDate parseDate(String value) {
    if (!StringUtils.hasText(value)) {
      throw new CustomException(ErrorCode.KIWOOM_CHART_MALFORMED_RESPONSE);
    }

    try {
      return LocalDate.parse(value.trim(), DATE_FORMAT);
    } catch (RuntimeException e) {
      log.warn("[KiwoomChart] 일자 파싱 실패: value={}", value);
      throw new CustomException(ErrorCode.KIWOOM_CHART_MALFORMED_RESPONSE);
    }
  }

  /**
   * 가격을 파싱한다.
   *
   * <p>키움 응답에서 부호가 붙는 필드는 "대비" 성격의 값(국내 {@code pred_pre},
   * {@code trde_tern_rt} / 미국 {@code pred_pre}, {@code flu_rt})이며, 가격 필드
   * ({@code cur_prc}, {@code open_pric}, {@code high_pric}, {@code low_pric})는 양수로 온다.
   * (2026-11-06 운영 환경에서 ka10081·usa06012 실제 응답으로 확인)
   *
   * <p>따라서 가격을 절댓값으로 보정하지 않는다. 0 이하 값이 오면 체결 근거로 쓸 수 없는
   * 응답이므로 거절한다. 조용히 보정하면 잘못된 가격으로 거래가 체결될 수 있다.
   */
  private BigDecimal parsePrice(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }

    BigDecimal price;
    try {
      price = new BigDecimal(value.trim());
    } catch (NumberFormatException e) {
      log.warn("[KiwoomChart] 가격 파싱 실패: value={}", value);
      throw new CustomException(ErrorCode.KIWOOM_CHART_MALFORMED_RESPONSE);
    }

    if (price.signum() <= 0) {
      log.warn("[KiwoomChart] 가격이 0 이하입니다: value={}", value);
      throw new CustomException(ErrorCode.KIWOOM_CHART_MALFORMED_RESPONSE);
    }

    return price;
  }

  private Long parseVolume(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }

    try {
      return new BigDecimal(value.trim()).longValue();
    } catch (NumberFormatException e) {
      log.warn("[KiwoomChart] 거래량 파싱 실패: value={}", value);
      return null;
    }
  }

  private <T extends KiwoomResponse> List<T> executePagingTemplate(
      String endpoint,
      String apiId,
      Object requestBody,
      Class<T> responseType
  ) {
    String contYn = "N";
    String nextKey = "";
    Set<String> seenNextKeys = new HashSet<>();
    List<T> allPages = new ArrayList<>();

    for (int page = 1; page <= maxPages; page++) {
      ResponseEntity<T> responseEntity = requestWithTokenRefresh(
          endpoint, apiId, requestBody, responseType, contYn, nextKey);
      T body = responseEntity.getBody();

      if (body == null) {
        throw new CustomException(ErrorCode.KIWOOM_EMPTY_RESPONSE);
      }

      validateKiwoomResponse(apiId, body);
      allPages.add(body);

      String responseContYn = responseEntity.getHeaders().getFirst("cont-yn");
      String responseNextKey = responseEntity.getHeaders().getFirst("next-key");
      log.info("[KiwoomChart] 요청 완료: apiId={}, page={}, contYn={}", apiId, page, responseContYn);

      if (!"Y".equalsIgnoreCase(responseContYn)) {
        return allPages;
      }

      if (!StringUtils.hasText(responseNextKey) || !seenNextKeys.add(responseNextKey)) {
        throw new CustomException(ErrorCode.KIWOOM_PAGING_LIMIT_EXCEEDED);
      }

      contYn = "Y";
      nextKey = responseNextKey;
    }

    throw new CustomException(ErrorCode.KIWOOM_PAGING_LIMIT_EXCEEDED);
  }

  private <T extends KiwoomResponse> ResponseEntity<T> requestWithTokenRefresh(
      String endpoint,
      String apiId,
      Object requestBody,
      Class<T> responseType,
      String contYn,
      String nextKey
  ) {
    String token = tokenManager.getValidToken();

    try {
      return request(endpoint, apiId, requestBody, responseType, contYn, nextKey, token);
    } catch (WebClientResponseException.Unauthorized e) {
      tokenManager.invalidateIfCurrent(token);
      log.warn("[KiwoomChart] 인증 실패 후 토큰 재발급 재시도: apiId={}", apiId);
      return request(
          endpoint, apiId, requestBody, responseType, contYn, nextKey,
          tokenManager.getValidToken());
    }
  }

  private <T extends KiwoomResponse> ResponseEntity<T> request(
      String endpoint,
      String apiId,
      Object requestBody,
      Class<T> responseType,
      String contYn,
      String nextKey,
      String token
  ) {
    try {
      return rateLimiter.call(() -> kiwoomWebClient.post()
          .uri(endpoint)
          .header("authorization", "Bearer " + token)
          .header("api-id", apiId)
          .header("cont-yn", contYn)
          .header("next-key", nextKey == null ? "" : nextKey)
          .bodyValue(requestBody)
          .retrieve()
          .toEntity(responseType)
          .block(blockTimeout));
    } catch (WebClientResponseException.Unauthorized e) {
      throw e;
    } catch (WebClientResponseException e) {
      log.warn("[KiwoomChart] HTTP 오류: apiId={}, status={}", apiId, e.getStatusCode());
      throw new CustomException(ErrorCode.KIWOOM_CHART_API_FAILED);
    } catch (WebClientRequestException | IllegalStateException e) {
      log.warn("[KiwoomChart] 요청 실패: apiId={}, message={}", apiId, e.getMessage());
      throw new CustomException(ErrorCode.KIWOOM_CHART_API_FAILED);
    }
  }

  private void validateKiwoomResponse(String apiId, KiwoomResponse response) {
    String returnCode = response.returnCode();
    if (StringUtils.hasText(returnCode) && !"0".equals(returnCode.trim())) {
      log.warn("[KiwoomChart] API 오류 응답: apiId={}, returnCode={}, returnMsg={}",
          apiId, returnCode, response.returnMsg());
      throw new CustomException(ErrorCode.KIWOOM_CHART_API_FAILED);
    }
  }
}
