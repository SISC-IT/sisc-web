package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KiwoomAccountClientTest {
  @Mock
  private KiwoomAuthClient authClient;

  private MockWebServer mockWebServer;
  private KiwoomAccountClient accountClient;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    when(authClient.issueAccessToken())
        .thenReturn(new KiwoomAccessToken("test-token", Instant.now().plus(Duration.ofHours(1))));

    WebClient webClient = WebClient.builder()
        .baseUrl(mockWebServer.url("/").toString())
        .build();

    accountClient = new KiwoomAccountClient(
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
  @DisplayName("재발급 토큰으로 재시도해도 401이면 KIWOOM_AUTH_FAILED로 변환한다")
  void convertsSecondUnauthorizedToAuthFailed() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(401));
    mockWebServer.enqueue(new MockResponse().setResponseCode(401));

    assertThatThrownBy(() -> accountClient.fetchAccounts())
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getErrorCode())
        .isEqualTo(ErrorCode.KIWOOM_AUTH_FAILED);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    // 캐시된 토큰을 무효화하고 새로 발급받아 재시도했는지 확인한다
    verify(authClient, times(2)).issueAccessToken();
  }

  @Test
  @DisplayName("첫 요청만 401이면 토큰 재발급 후 재시도해 성공한다")
  void retriesOnceAfterUnauthorizedAndSucceeds() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(401));
    mockWebServer.enqueue(new MockResponse()
        .setBody("{\"acctNo\":\"1234567890\",\"return_code\":\"0\"}")
        .addHeader("Content-Type", "application/json"));

    assertThat(accountClient.fetchAccounts()).hasSize(1);
    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    // 캐시된 토큰을 무효화하고 새로 발급받아 재시도했는지 확인한다
    verify(authClient, times(2)).issueAccessToken();
  }
}
