package org.sejongisc.backend.disclosure.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

enum EarningsAccount {
  REVENUE(Set.of("ifrs-full_revenue", "ifrs_revenue", "dart_revenue"),
      Set.of("매출액", "수익(매출액)")),
  OPERATING_PROFIT(Set.of("dart_operatingincomeloss", "ifrs-full_operatingprofitloss"),
      Set.of("영업이익", "영업이익(손실)", "영업손익")),
  NET_INCOME(Set.of("ifrs-full_profitloss", "ifrs_profitloss"),
      Set.of("당기순이익", "당기순이익(손실)", "분기순이익", "분기순이익(손실)",
          "반기순이익", "반기순이익(손실)"));

  static final List<String> METRIC_TYPES = Arrays.stream(values()).map(Enum::name).toList();
  static final String CURRENT = "CURRENT_PERIOD";
  static final String ANNUAL = "ANNUAL";
  static final String YEAR_TO_DATE = "YEAR_TO_DATE";
  static final String CURRENT_AMOUNT = "thstrm_amount";
  static final String CUMULATIVE_AMOUNT = "thstrm_add_amount";

  private final Set<String> standardIds;
  private final Set<String> names;

  EarningsAccount(Set<String> standardIds, Set<String> names) {
    this.standardIds = standardIds;
    this.names = names;
  }

  static boolean hasAmounts(List<Map<String, String>> rows, boolean needsCumulative) {
    for (EarningsAccount account : values()) {
      if (account.select(rows, CURRENT_AMOUNT) == null
          || (needsCumulative && account.select(rows, CUMULATIVE_AMOUNT) == null)) {
        return false;
      }
    }
    return true;
  }

  Map<String, String> select(List<Map<String, String>> rows, String amountField) {
    int bestRank = 0;
    Map<String, String> best = null;
    BigDecimal bestAmount = null;
    boolean ambiguous = false;
    for (Map<String, String> row : rows) {
      int rank = rank(row);
      if (rank == 0 || rank < bestRank) {
        continue;
      }
      BigDecimal amount = amount(row.get(amountField));
      String currency = row.get("currency");
      if (amount == null || currency == null || !currency.matches("[A-Z]{3}")) {
        continue;
      }
      if (rank > bestRank) {
        best = row;
        bestAmount = amount;
        bestRank = rank;
        ambiguous = false;
      } else if (bestAmount.compareTo(amount) != 0 || !best.get("currency").equals(currency)) {
        ambiguous = true;
      }
    }
    return ambiguous ? null : best;
  }

  int rank(Map<String, String> row) {
    String statement = row.get("sj_div");
    if (!"IS".equals(statement) && !"CIS".equals(statement)) {
      return 0;
    }
    String id = text(row.get("account_id")).toLowerCase(Locale.ROOT);
    if (standardIds.contains(id)) {
      return 2;
    }
    // A different standard account must not be mapped through a similar Korean label.
    if (id.startsWith("ifrs") || id.startsWith("dart_")) {
      return 0;
    }
    return names.contains(text(row.get("account_nm")).replaceAll("\\s+", "")) ? 1 : 0;
  }

  static boolean sameAccount(Map<String, String> first, Map<String, String> second) {
    String firstId = text(first.get("account_id"));
    String secondId = text(second.get("account_id"));
    if (!firstId.equals(secondId)) {
      return false;
    }
    return (!firstId.isBlank() && !firstId.startsWith("-"))
        || text(first.get("account_nm")).equals(text(second.get("account_nm")));
  }

  static BigDecimal amount(String value) {
    if (value == null || value.isBlank() || "-".equals(value.trim())) {
      return null;
    }
    try {
      return new BigDecimal(value.replace(",", "").trim());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }
}
