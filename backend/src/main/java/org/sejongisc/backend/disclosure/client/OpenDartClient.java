package org.sejongisc.backend.disclosure.client;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.sejongisc.backend.disclosure.config.OpenDartProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

@Component
public class OpenDartClient {
  private final WebClient webClient;
  private final OpenDartProperties properties;

  public OpenDartClient(@Qualifier("openDartWebClient") WebClient webClient,
      OpenDartProperties properties) {
    this.webClient = webClient;
    this.properties = properties;
  }

  public DartDisclosurePage searchRegularReports(String corpCode, LocalDate from, LocalDate to,
      int page, int size) {
    return request("/list.json", corpCode, uri -> uri
        .queryParam("pblntf_ty", "A")
        .queryParam("bgn_de", from.format(DateTimeFormatter.BASIC_ISO_DATE))
        .queryParam("end_de", to.format(DateTimeFormatter.BASIC_ISO_DATE))
        .queryParam("last_reprt_at", "N")
        .queryParam("page_no", page)
        .queryParam("page_count", Math.min(Math.max(size, 1), 100))
        .queryParam("sort", "date")
        .queryParam("sort_mth", "desc"), DartDisclosurePage.class);
  }

  public DartCompany company(String corpCode) {
    return request("/company.json", corpCode, Function.identity(), DartCompany.class);
  }

  public DartFinancialResponse financialStatements(String corpCode, int businessYear,
      String reportCode, String basis) {
    return request("/fnlttSinglAcntAll.json", corpCode, uri -> uri
        .queryParam("bsns_year", businessYear)
        .queryParam("reprt_code", reportCode)
        .queryParam("fs_div", basis), DartFinancialResponse.class);
  }

  private <T> T request(String path, String corpCode, Function<UriBuilder, UriBuilder> parameters,
      Class<T> responseType) {
    if (properties.key() == null || properties.key().isBlank()) {
      throw new IllegalStateException("OPENDART_API_KEY is not configured");
    }
    return webClient.get().uri(uri -> parameters.apply(uri.path(path)
                .queryParam("crtfc_key", properties.key())
                .queryParam("corp_code", corpCode))
            .build())
        .retrieve()
        .bodyToMono(responseType)
        .timeout(Duration.ofMillis(Math.max(properties.timeoutMs(), 1000)))
        // Transport exceptions may include the request URI and its authentication query parameter.
        .onErrorMap(error -> new OpenDartClientException("OpenDART request failed: " + path))
        .block();
  }

  public record DartCompany(String status, String acc_mt) {
  }

  public record DartDisclosurePage(String status, String message, Integer page_no,
      Integer page_count, Integer total_count, Integer total_page,
      List<Map<String, String>> list) {
  }

  public record DartFinancialResponse(String status, String message,
      List<Map<String, String>> list) {
  }
}
