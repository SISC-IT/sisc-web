package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class KiwoomWebClientConfigTest {
  private MockWebServer mockWebServer;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  @DisplayName("기본 버퍼(256KB)를 넘는 응답도 읽는다")
  void readsResponseLargerThanDefaultBuffer() {
    String body = "{\"name\":\"" + "가".repeat(200_000) + "\"}";
    mockWebServer.enqueue(new MockResponse()
        .setBody(body)
        .addHeader("Content-Type", "application/json"));

    WebClient webClient = new KiwoomWebClientConfig().kiwoomWebClient(
        WebClient.builder(), mockWebServer.url("/").toString(), 3000, 5000, 5 * 1024 * 1024);

    String response = webClient.post().uri("/").retrieve().bodyToMono(String.class).block();

    assertThat(response).hasSize(body.length());
  }
}
