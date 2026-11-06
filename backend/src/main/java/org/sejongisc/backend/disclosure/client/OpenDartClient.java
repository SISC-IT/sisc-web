package org.sejongisc.backend.disclosure.client;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import org.sejongisc.backend.disclosure.config.OpenDartProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class OpenDartClient {
  private final WebClient webClient;
  private final OpenDartProperties properties;

  public OpenDartClient(@Qualifier("openDartWebClient") WebClient webClient,
      OpenDartProperties properties) {
    this.webClient = webClient;
    this.properties = properties;
  }

  public DartDisclosurePage search(String corpCode, LocalDate from, LocalDate to,
      int page, int size) {
    if (properties.key() == null || properties.key().isBlank()) {
      throw new IllegalStateException("OPENDART_API_KEY is not configured");
    }
    return webClient.get().uri(uri -> uri.path("/list.json")
            .queryParam("crtfc_key", properties.key())
            .queryParam("corp_code", corpCode)
            .queryParam("bgn_de", from.toString().replace("-", ""))
            .queryParam("end_de", to.toString().replace("-", ""))
            .queryParam("last_reprt_at", "N")
            .queryParam("page_no", page)
            .queryParam("page_count", Math.min(Math.max(size, 1), 100))
            .queryParam("sort", " date ".trim())
            .queryParam("sort_mth", "desc")
            .build())
        .retrieve()
        .bodyToMono(DartDisclosurePage.class)
        .timeout(Duration.ofMillis(Math.max(properties.timeoutMs(), 1000)))
        .onErrorMap(error -> new OpenDartClientException("OpenDART request failed", error))
        .onErrorResume(OpenDartClientException.class, Mono::error)
        .block();
  }

  public DartFinancialResponse financialStatements(String corpCode, int businessYear,
      String reportCode, String basis) {
    if (properties.key() == null || properties.key().isBlank()) {
      throw new IllegalStateException("OPENDART_API_KEY is not configured");
    }
    return webClient.get().uri(uri -> uri.path("/fnlttSinglAcntAll.json")
            .queryParam("crtfc_key", properties.key())
            .queryParam("corp_code", corpCode)
            .queryParam("bsns_year", businessYear)
            .queryParam("reprt_code", reportCode)
            .queryParam("fs_div", basis)
            .build())
        .retrieve()
        .bodyToMono(DartFinancialResponse.class)
        .timeout(Duration.ofMillis(Math.max(properties.timeoutMs(), 1000)))
        .onErrorMap(error -> new OpenDartClientException("OpenDART financial request failed", error))
        .onErrorResume(OpenDartClientException.class, Mono::error)
        .block();
  }

  public record DartDisclosurePage(String status, String message, Integer page_no,
      Integer page_count, Integer total_count, Integer total_page,
      java.util.List<Map<String, String>> list) {
  }

  public record DartFinancialResponse(String status, String message,
      java.util.List<Map<String, String>> list) {
  }
}
