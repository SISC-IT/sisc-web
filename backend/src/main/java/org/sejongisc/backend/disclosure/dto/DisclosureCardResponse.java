package org.sejongisc.backend.disclosure.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DisclosureCardResponse(
    UUID cardId,
    String receiptNo,
    String category,
    String categoryLabel,
    String corpCode,
    String companyName,
    String stockCode,
    String market,
    String indutyCode,
    String reportTitle,
    String headline,
    LocalDate publishedDate,
    List<Metric> metrics,
    List<String> tags,
    String dartUrl) {

  public record Metric(String label, String value, boolean highlight) {
  }

  public record PageResponse(
      List<DisclosureCardResponse> items,
      long totalCount,
      int page,
      int size,
      int totalPages) {
  }
}
