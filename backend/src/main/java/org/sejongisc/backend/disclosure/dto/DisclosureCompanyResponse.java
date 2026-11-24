package org.sejongisc.backend.disclosure.dto;

import java.time.LocalDate;
import java.util.List;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Quarter;

public final class DisclosureCompanyResponse {
  private DisclosureCompanyResponse() {
  }

  public record CompanyListItem(
      String corpCode,
      String companyName,
      String stockCode,
      String market,
      boolean trackCapex,
      LocalDate latestEventDate,
      long eventCount,
      boolean hasUnread) {
  }

  public record CompanyListResponse(
      List<CompanyListItem> items,
      long totalCount,
      int page,
      int size,
      int totalPages) {
  }

  public record CompanyDetailResponse(
      String corpCode,
      String companyName,
      String stockCode,
      String market,
      String indutyCode,
      boolean trackCapex,
      Quarter latestQuarter,
      List<Quarter> earningsTrend,
      List<DisclosureCardResponse> events) {
  }
}
