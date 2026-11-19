package org.sejongisc.backend.disclosure.service;

import java.math.BigDecimal;
import java.util.Map;

record EarningsRow(
    String statementDivision,
    String accountId,
    String accountName,
    String currency,
    BigDecimal currentAmount,
    BigDecimal cumulativeAmount,
    Map<String, String> rawPayload
) {
  enum AmountKind {
    CURRENT,
    CUMULATIVE
  }

  static EarningsRow fromDartRow(Map<String, String> row) {
    return new EarningsRow(
        blankToNull(row.get("sj_div")),
        blankToNull(row.get("account_id")),
        row.getOrDefault("account_nm", ""),
        blankToNull(row.get("currency")),
        parseAmount(row.get("thstrm_amount")),
        parseAmount(row.get("thstrm_add_amount")),
        row
    );
  }

  BigDecimal amount(AmountKind kind) {
    return kind == AmountKind.CURRENT ? currentAmount : cumulativeAmount;
  }

  boolean hasValidCurrency() {
    return currency != null && currency.matches("[A-Z]{3}");
  }

  static BigDecimal parseAmount(String value) {
    if (value == null || value.isBlank() || "-".equals(value.trim())) {
      return null;
    }
    try {
      return new BigDecimal(value.replace(",", "").trim());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
