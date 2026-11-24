package org.sejongisc.backend.disclosure.service.enums;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.sejongisc.backend.disclosure.service.enums.EarningsRow.AmountKind;

public enum EarningsAccount {
  REVENUE(Set.of("ifrs-full_revenue", "ifrs_revenue", "dart_revenue"),
      Set.of("매출액", "수익(매출액)")),
  OPERATING_PROFIT(Set.of("dart_operatingincomeloss", "ifrs-full_operatingprofitloss"),
      Set.of("영업이익", "영업이익(손실)", "영업손익")),
  NET_INCOME(Set.of("ifrs-full_profitloss", "ifrs_profitloss"),
      Set.of("당기순이익", "당기순이익(손실)", "분기순이익", "분기순이익(손실)",
          "반기순이익", "반기순이익(손실)"));

  public static final List<String> METRIC_TYPES = Arrays.stream(values()).map(Enum::name).toList();
  public static final String CURRENT = "CURRENT_PERIOD";
  public static final String ANNUAL = "ANNUAL";
  public static final String YEAR_TO_DATE = "YEAR_TO_DATE";

  private final Set<String> standardIds;
  private final Set<String> names;

  EarningsAccount(Set<String> standardIds, Set<String> names) {
    this.standardIds = standardIds;
    this.names = names;
  }

  public static boolean hasAmounts(List<EarningsRow> rows, boolean needsCumulative) {
    for (EarningsAccount account : values()) {
      if (account.select(rows, AmountKind.CURRENT) == null
          || (needsCumulative && account.select(rows, AmountKind.CUMULATIVE) == null)) {
        return false;
      }
    }
    return true;
  }

  public EarningsRow select(List<EarningsRow> rows, AmountKind kind) {
    int bestRank = 0;
    EarningsRow best = null;
    BigDecimal bestAmount = null;
    boolean ambiguous = false;

    for (EarningsRow row : rows) {
      int rank = rank(row);
      if (rank == 0 || rank < bestRank) {
        continue;
      }
      BigDecimal amount = row.amount(kind);
      if (amount == null || !row.hasValidCurrency()) {
        continue;
      }
      if (rank > bestRank) {
        best = row;
        bestAmount = amount;
        bestRank = rank;
        ambiguous = false;
      } else if (bestAmount.compareTo(amount) != 0 || !best.currency().equals(row.currency())) {
        ambiguous = true;
      }
    }
    return ambiguous ? null : best;
  }

  public int rank(EarningsRow row) {
    String statement = row.statementDivision();
    if (!"IS".equals(statement) && !"CIS".equals(statement)) {
      return 0;
    }
    String id = text(row.accountId()).toLowerCase(Locale.ROOT);
    if (standardIds.contains(id)) {
      return 2;
    }
    // A different standard account must not be mapped through a similar Korean label.
    if (id.startsWith("ifrs") || id.startsWith("dart_")) {
      return 0;
    }
    return names.contains(text(row.accountName()).replaceAll("\\s+", "")) ? 1 : 0;
  }

  public static boolean sameAccount(EarningsRow first, EarningsRow second) {
    String firstId = text(first.accountId());
    String secondId = text(second.accountId());
    if (!firstId.equals(secondId)) {
      return false;
    }
    return (!firstId.isBlank() && !firstId.startsWith("-"))
        || text(first.accountName()).equals(text(second.accountName()));
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }
}
