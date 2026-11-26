package org.sejongisc.backend.disclosure.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.sejongisc.backend.disclosure.config.OpenDartProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

@Component
public class OpenDartClient {
  private static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
  private final WebClient webClient;
  private final OpenDartProperties properties;

  public OpenDartClient(@Qualifier("openDartWebClient") WebClient webClient,
      OpenDartProperties properties) {
    this.webClient = webClient;
    this.properties = properties;
  }

  public DartDisclosurePage searchRegularReports(String corpCode, LocalDate from, LocalDate to,
      int page, int size) {
    return searchDisclosures(corpCode, "A", from, to, page, size, false);
  }

  public DartDisclosurePage searchDisclosures(String corpCode, String disclosureType,
      LocalDate from, LocalDate to, int page, int size, boolean onlyLatestReport) {
    return requestOptionalCorp("/list.json", corpCode, uri -> {
      UriBuilder builder = uri
          .queryParam("bgn_de", from.format(DateTimeFormatter.BASIC_ISO_DATE))
          .queryParam("end_de", to.format(DateTimeFormatter.BASIC_ISO_DATE))
          .queryParam("last_reprt_at", onlyLatestReport ? "Y" : "N")
          .queryParam("page_no", page)
          .queryParam("page_count", Math.min(Math.max(size, 1), 100))
          .queryParam("sort", "date")
          .queryParam("sort_mth", "desc");
      if (disclosureType != null && !disclosureType.isBlank()) {
        builder = builder.queryParam("pblntf_ty", disclosureType);
      }
      return builder;
    }, DartDisclosurePage.class);
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

  public DartListResponse treasuryStockAcquisitions(String corpCode, LocalDate from, LocalDate to) {
    return request("/tsstkAqDecsn.json", corpCode, uri -> uri
        .queryParam("bgn_de", from.format(DateTimeFormatter.BASIC_ISO_DATE))
        .queryParam("end_de", to.format(DateTimeFormatter.BASIC_ISO_DATE)), DartListResponse.class);
  }

  public String downloadDocument(String receiptNo) {
    if (properties.key() == null || properties.key().isBlank()) {
      throw new IllegalStateException("OPENDART_API_KEY is not configured");
    }
    byte[] payload = webClient.get().uri(uri -> uri.path("/document.xml")
            .queryParam("crtfc_key", properties.key())
            .queryParam("rcept_no", receiptNo)
            .build())
        .retrieve()
        .bodyToMono(byte[].class)
        .timeout(Duration.ofMillis(Math.max(properties.timeoutMs(), 1000)))
        .onErrorMap(error -> new OpenDartClientException("OpenDART request failed: /document.xml"))
        .block();
    if (payload == null || payload.length == 0) {
      return "";
    }
    return unzipFirstXmlOrText(payload);
  }

  private String unzipFirstXmlOrText(byte[] payload) {
    // ZIP magic bytes: PK\x03\x04
    if (payload.length >= 4 && payload[0] == 0x50 && payload[1] == 0x4B) {
      try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(payload))) {
        ZipEntry entry;
        StringBuilder combined = new StringBuilder();
        int remaining = MAX_DOCUMENT_BYTES;
        while ((entry = zis.getNextEntry()) != null) {
          if (entry.isDirectory()) {
            continue;
          }
          byte[] entryBytes = zis.readNBytes(remaining + 1);
          if (entryBytes.length > remaining) {
            throw new OpenDartClientException("OpenDART document exceeds the unpacked size limit");
          }
          remaining -= entryBytes.length;
          combined.append(decodeXmlText(entryBytes)).append('\n');
        }
        return combined.toString();
      } catch (IOException e) {
        throw new OpenDartClientException("Failed to unpack OpenDART document archive");
      }
    }
    return decodeXmlText(payload);
  }

  private String decodeXmlText(byte[] bytes) {
    String utf8 = new String(bytes, StandardCharsets.UTF_8);
    if (utf8.contains("euc-kr") || utf8.contains("EUC-KR")) {
      return new String(bytes, Charset.forName("EUC-KR"));
    }
    return utf8;
  }

  private <T> T request(String path, String corpCode, Function<UriBuilder, UriBuilder> parameters,
      Class<T> responseType) {
    return requestOptionalCorp(path, corpCode, parameters, responseType);
  }

  private <T> T requestOptionalCorp(String path, String corpCode,
      Function<UriBuilder, UriBuilder> parameters, Class<T> responseType) {
    if (properties.key() == null || properties.key().isBlank()) {
      throw new IllegalStateException("OPENDART_API_KEY is not configured");
    }
    return webClient.get().uri(uri -> {
          UriBuilder builder = uri.path(path).queryParam("crtfc_key", properties.key());
          if (corpCode != null && !corpCode.isBlank()) {
            builder = builder.queryParam("corp_code", corpCode);
          }
          return parameters.apply(builder).build();
        })
        .retrieve()
        .bodyToMono(responseType)
        .timeout(Duration.ofMillis(Math.max(properties.timeoutMs(), 1000)))
        // Transport exceptions may include the request URI and its authentication query parameter.
        .onErrorMap(error -> new OpenDartClientException("OpenDART request failed: " + path))
        .block();
  }

  public record DartCompany(String status, String acc_mt, String induty_code,
      String corp_name, String stock_code, String corp_cls) {
    public DartCompany(String status, String acc_mt) {
      this(status, acc_mt, null, null, null, null);
    }
  }

  public record DartDisclosurePage(String status, String message, Integer page_no,
      Integer page_count, Integer total_count, Integer total_page,
      List<Map<String, String>> list) {
  }

  public record DartFinancialResponse(String status, String message,
      List<Map<String, String>> list) {
  }

  public record DartListResponse(String status, String message,
      List<Map<String, String>> list) {
  }
}
