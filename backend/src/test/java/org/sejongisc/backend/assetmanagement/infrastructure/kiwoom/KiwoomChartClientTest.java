package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sejongisc.backend.assetmanagement.model.DailyCandle;
import org.sejongisc.backend.assetmanagement.model.UsExchangeType;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KiwoomChartClientTest {
  @Mock
  private KiwoomAuthClient authClient;

  private MockWebServer mockWebServer;
  private KiwoomChartClient chartClient;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    when(authClient.issueAccessToken())
        .thenReturn(new KiwoomAccessToken("test-token", Instant.now().plus(Duration.ofHours(1))));

    WebClient webClient = WebClient.builder()
        .baseUrl(mockWebServer.url("/").toString())
        .build();

    chartClient = new KiwoomChartClient(
        webClient,
        new KiwoomTokenManager(authClient, 300),
        new KiwoomRateLimiter(3, 0),
        20,
        5000
    );
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  @DisplayName("국내 일봉을 조회하면 일자 오름차순으로 정규화된다")
  void fetchesDomesticDailyCandlesInAscendingDateOrder() throws Exception {
    // 키움은 기준일자부터 과거 방향으로 응답한다
    enqueueJson("""
        {
          "stk_cd": "005930",
          "stk_dt_pole_chart_qry": [
            {"dt":"20261105","cur_prc":"61000","open_pric":"60500","high_pric":"61500","low_pric":"60000","trde_qty":"12000000"},
            {"dt":"20261104","cur_prc":"60000","open_pric":"59000","high_pric":"60200","low_pric":"58900","trde_qty":"9000000"}
          ],
          "return_code": "0"
        }
        """);

    List<DailyCandle> candles =
        chartClient.fetchDomesticDailyCandles("005930", LocalDate.of(2026, 11, 5));

    assertThat(candles).hasSize(2);
    assertThat(candles).extracting(DailyCandle::date)
        .containsExactly(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5));
    assertThat(candles.getFirst().closePrice()).isEqualByComparingTo("60000");
    assertThat(candles.getFirst().currency()).isEqualTo("KRW");
    assertThat(candles.getFirst().volume()).isEqualTo(9_000_000L);

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getPath()).isEqualTo("/api/dostk/chart");
    assertThat(request.getHeader("api-id")).isEqualTo("ka10081");
    assertThat(request.getHeader("authorization")).isEqualTo("Bearer test-token");

    String body = request.getBody().readUtf8();
    assertThat(body).contains("\"stk_cd\":\"005930\"");
    assertThat(body).contains("\"base_dt\":\"20261105\"");
    assertThat(body).contains("\"upd_stkpc_tp\":\"1\"");
  }

  @Test
  @DisplayName("하락일에도 가격 필드는 양수이고 부호는 대비 필드에만 붙는다")
  void keepsPricesPositiveOnDecliningDay() {
    // 2026-11-06 운영 환경 ka10081 실제 응답 형태.
    // 11/5는 하락일(221500 -> 201500)이지만 cur_prc는 양수이고,
    // 부호는 pred_pre(전일대비)와 trde_tern_rt(거래회전율)에만 붙는다.
    enqueueJson("""
        {
          "stk_cd": "005930",
          "stk_dt_pole_chart_qry": [
            {"dt":"20261105","cur_prc":"201500","open_pric":"213000","high_pric":"213500","low_pric":"199800","trde_qty":"24657743","pred_pre":"-16500","pred_pre_sig":"5","trde_tern_rt":"+0.42"}
          ],
          "return_code": "0"
        }
        """);

    List<DailyCandle> candles =
        chartClient.fetchDomesticDailyCandles("005930", LocalDate.of(2026, 11, 5));

    DailyCandle candle = candles.getFirst();
    assertThat(candle.closePrice()).isEqualByComparingTo("201500");
    assertThat(candle.openPrice()).isEqualByComparingTo("213000");
    assertThat(candle.highPrice()).isEqualByComparingTo("213500");
    assertThat(candle.lowPrice()).isEqualByComparingTo("199800");
    assertThat(candle.volume()).isEqualTo(24_657_743L);
  }

  @Test
  @DisplayName("가격이 0 이하면 체결 근거로 쓸 수 없어 거절한다")
  void rejectsNonPositivePrice() {
    // 가격을 조용히 절댓값으로 보정하면 잘못된 가격으로 거래가 체결될 수 있다.
    enqueueJson("""
        {
          "stk_cd": "005930",
          "stk_dt_pole_chart_qry": [
            {"dt":"20261104","cur_prc":"-60000","open_pric":"59000","high_pric":"60200","low_pric":"58900","trde_qty":"9000000"}
          ],
          "return_code": "0"
        }
        """);

    assertThatThrownBy(() ->
        chartClient.fetchDomesticDailyCandles("005930", LocalDate.of(2026, 11, 4)))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.KIWOOM_CHART_MALFORMED_RESPONSE);
  }

  @Test
  @DisplayName("종가가 누락되면 거절한다")
  void rejectsMissingClosePrice() {
    // 종가가 null인 캔들이 흘러가면 호출자에서 NPE가 나거나 잘못된 값이 저장된다.
    enqueueJson("""
        {
          "stk_cd": "005930",
          "stk_dt_pole_chart_qry": [
            {"dt":"20261104","open_pric":"59000","high_pric":"60200","low_pric":"58900","trde_qty":"9000000"}
          ],
          "return_code": "0"
        }
        """);

    assertThatThrownBy(() ->
        chartClient.fetchDomesticDailyCandles("005930", LocalDate.of(2026, 11, 4)))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.KIWOOM_CHART_MALFORMED_RESPONSE);
  }

  @Test
  @DisplayName("종가가 빈 문자열이면 거절한다")
  void rejectsBlankClosePrice() {
    enqueueJson("""
        {
          "stk_cd": "005930",
          "stk_dt_pole_chart_qry": [
            {"dt":"20261104","cur_prc":"   ","open_pric":"59000","trde_qty":"9000000"}
          ],
          "return_code": "0"
        }
        """);

    assertThatThrownBy(() ->
        chartClient.fetchDomesticDailyCandles("005930", LocalDate.of(2026, 11, 4)))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.KIWOOM_CHART_MALFORMED_RESPONSE);
  }

  @Test
  @DisplayName("종가만 있으면 시가·고가·저가는 null로 조회된다")
  void allowsMissingOhlcWhenClosePriceExists() {
    // 키움이 시가·고가·저가를 생략해 보내는 경우가 있어 종가만 필수로 둔다.
    enqueueJson("""
        {
          "stk_cd": "005930",
          "stk_dt_pole_chart_qry": [
            {"dt":"20261104","cur_prc":"60000"}
          ],
          "return_code": "0"
        }
        """);

    List<DailyCandle> candles =
        chartClient.fetchDomesticDailyCandles("005930", LocalDate.of(2026, 11, 4));

    DailyCandle candle = candles.getFirst();
    assertThat(candle.closePrice()).isEqualByComparingTo("60000");
    assertThat(candle.openPrice()).isNull();
    assertThat(candle.highPrice()).isNull();
    assertThat(candle.lowPrice()).isNull();
  }

  @Test
  @DisplayName("미국 일봉은 USD 원본 종가로 조회된다")
  void fetchesUsDailyCandlesInUsd() throws Exception {
    enqueueJson("""
        {
          "result_list": [
            {"dt":"20261105","cur_prc":"342.3400","pred_pre":"-7.1700","flu_rt":"-2.09","open_pric":"353.8900","high_pric":"355.3800","low_pric":"342.0600","acc_trde_qty":"75678945"}
          ],
          "return_code": "0"
        }
        """);

    List<DailyCandle> candles = chartClient.fetchUsDailyCandles(
        UsExchangeType.NASDAQ, "AAPL", LocalDate.of(2026, 11, 5));

    assertThat(candles).hasSize(1);
    DailyCandle candle = candles.getFirst();
    assertThat(candle.ticker()).isEqualTo("AAPL");
    // 소수점 4자리가 보존되어야 한다 (환산 전 USD 원본)
    assertThat(candle.closePrice()).isEqualByComparingTo(new BigDecimal("342.3400"));
    assertThat(candle.currency()).isEqualTo("USD");

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getPath()).isEqualTo("/api/us/chart");
    assertThat(request.getHeader("api-id")).isEqualTo("usa06012");

    String body = request.getBody().readUtf8();
    assertThat(body).contains("\"stex_tp\":\"ND\"");
    assertThat(body).contains("\"stk_cd\":\"AAPL\"");
    // 환율 미적용: 환산은 운영진 설정 환율로 서버에서 처리한다
    assertThat(body).contains("\"exrt_appl_tp\":\"0\"");
  }

  @Test
  @DisplayName("cont-yn이 Y면 next-key로 연속 조회한다")
  void followsPaginationWhenContYnIsY() throws Exception {
    mockWebServer.enqueue(new MockResponse()
        .setBody("""
            {"stk_cd":"005930","stk_dt_pole_chart_qry":[{"dt":"20261105","cur_prc":"61000"}],"return_code":"0"}
            """)
        .addHeader("Content-Type", "application/json")
        .addHeader("cont-yn", "Y")
        .addHeader("next-key", "key-page-2"));
    enqueueJson("""
        {"stk_cd":"005930","stk_dt_pole_chart_qry":[{"dt":"20261104","cur_prc":"60000"}],"return_code":"0"}
        """);

    List<DailyCandle> candles =
        chartClient.fetchDomesticDailyCandles("005930", LocalDate.of(2026, 11, 5));

    assertThat(candles).hasSize(2);
    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    mockWebServer.takeRequest();
    RecordedRequest secondRequest = mockWebServer.takeRequest();
    assertThat(secondRequest.getHeader("cont-yn")).isEqualTo("Y");
    assertThat(secondRequest.getHeader("next-key")).isEqualTo("key-page-2");
  }

  @Test
  @DisplayName("return_code가 0이 아니면 예외를 던진다")
  void throwsWhenReturnCodeIsNotZero() {
    enqueueJson("""
        {"return_code":"3", "return_msg":"종목코드 오류"}
        """);

    assertThatThrownBy(() ->
        chartClient.fetchDomesticDailyCandles("000000", LocalDate.of(2026, 11, 4)))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.KIWOOM_CHART_API_FAILED);
  }

  @Test
  @DisplayName("종목코드가 비어 있으면 호출 전에 거절한다")
  void rejectsBlankStockCodeWithoutCallingApi() {
    assertThatThrownBy(() -> chartClient.fetchDomesticDailyCandles("  ", LocalDate.of(2026, 11, 4)))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_KIWOOM_STOCK_CODE);

    assertThat(mockWebServer.getRequestCount()).isZero();
  }

  @Test
  @DisplayName("일자 형식이 깨져 있으면 예외를 던진다")
  void throwsOnMalformedDate() {
    enqueueJson("""
        {"stk_cd":"005930","stk_dt_pole_chart_qry":[{"dt":"2026-11-04","cur_prc":"60000"}],"return_code":"0"}
        """);

    assertThatThrownBy(() ->
        chartClient.fetchDomesticDailyCandles("005930", LocalDate.of(2026, 11, 4)))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.KIWOOM_CHART_MALFORMED_RESPONSE);
  }

  @Test
  @DisplayName("지원하지 않는 미국 거래소는 거절한다")
  void rejectsUnknownUsExchangeType() {
    assertThatThrownBy(() -> UsExchangeType.from("LSE"))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_KIWOOM_US_EXCHANGE_TYPE);
  }

  @Test
  @DisplayName("미국 거래소는 이름과 코드 양쪽으로 해석된다")
  void resolvesUsExchangeTypeByNameAndCode() {
    assertThat(UsExchangeType.from("NASDAQ")).isEqualTo(UsExchangeType.NASDAQ);
    assertThat(UsExchangeType.from("nd")).isEqualTo(UsExchangeType.NASDAQ);
    assertThat(UsExchangeType.from("NY")).isEqualTo(UsExchangeType.NYSE);
    assertThat(UsExchangeType.NASDAQ.code()).isEqualTo("ND");
  }

  private void enqueueJson(String body) {
    mockWebServer.enqueue(new MockResponse()
        .setBody(body)
        .addHeader("Content-Type", "application/json"));
  }
}
