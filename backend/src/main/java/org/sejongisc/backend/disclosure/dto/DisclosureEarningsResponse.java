package org.sejongisc.backend.disclosure.dto;

import java.time.LocalDate;
import java.util.List;

public record DisclosureEarningsResponse(String corpCode, String companyName, String stockCode,
    List<Quarter> quarters) {

  public record Quarter(int fiscalYear, int quarter, String periodEnd, String basis, String currency,
      String revenue, String operatingProfit, String netIncome, List<Source> sources) {
  }

  public record Source(String receiptNo, LocalDate receivedDate, String url) {
  }
}
