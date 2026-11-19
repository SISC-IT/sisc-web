package org.sejongisc.backend.disclosure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.disclosure.config.OpenDartProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class OpenDartClientTest {
  private static final String BASE_URL = "https://fixture.invalid/api";
  private static final String COMPANY = "00000001";

  @Test
  void decodesFiscalClosingMonthAndRequestsRegularReportsIncludingCorrections() {
    List<ClientRequest> requests = new ArrayList<>();
    var client = client(WebClient.builder().exchangeFunction(request -> {
      requests.add(request);
      String body = request.url().getPath().endsWith("/company.json")
          ? "{\"status\":\"000\",\"acc_mt\":\"03\"}"
          : "{\"status\":\"013\"}";
      return Mono.just(ClientResponse.create(HttpStatus.OK)
          .header("Content-Type", "application/json").body(body).build());
    }));

    assertThat(client.company(COMPANY).acc_mt()).isEqualTo("03");
    assertThat(client.searchRegularReports(COMPANY, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 19),
        2, 100).status()).isEqualTo("013");
    assertThat(requests).extracting(request -> request.url().getPath())
        .containsExactly("/api/company.json", "/api/list.json");
    assertThat(requests.getLast().url().getQuery()).contains("pblntf_ty=A", "last_reprt_at=N",
        "corp_code=00000001", "bgn_de=20250101", "end_de=20261119", "page_no=2");
  }

  @Test
  void transportErrorDoesNotExposeTheRequestUriOrKey() {
    var client = client(WebClient.builder().exchangeFunction(request ->
        Mono.error(new IllegalStateException(request.url().toString()))));

    assertThatThrownBy(() -> client.company(COMPANY))
        .isInstanceOf(OpenDartClientException.class)
        .hasMessage("OpenDART request failed: /company.json")
        .hasNoCause();
  }

  private OpenDartClient client(WebClient.Builder builder) {
    return new OpenDartClient(builder.baseUrl(BASE_URL).build(),
        new OpenDartProperties(BASE_URL, "fixture-only", 10000));
  }
}
