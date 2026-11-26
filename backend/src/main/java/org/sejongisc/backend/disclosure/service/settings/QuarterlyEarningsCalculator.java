package org.sejongisc.backend.disclosure.service.settings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Growth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Quarter;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Source;
import org.sejongisc.backend.disclosure.service.enums.EarningsAccount;
import org.sejongisc.backend.disclosure.service.enums.EarningsRow;
import org.sejongisc.backend.disclosure.service.enums.EarningsRow.AmountKind;
import org.sejongisc.backend.disclosure.service.enums.ReportingQuarter;
import org.springframework.stereotype.Component;

// 분기 실적 요약 및 4분기 역산 계산기
@Component
public class QuarterlyEarningsCalculator {
  private static final String DART_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";

  public Quarter summarize(ReportingQuarter quarter, Map<ReportingQuarter, List<StoredFiling>> reports) {
    Quarter current = summarizeAmounts(quarter, reports);
    return new Quarter(current.fiscalYear(), current.quarter(), current.periodEnd(), current.basis(),
        current.currency(), current.revenue(), current.operatingProfit(), current.netIncome(), current.sources(),
        growth(current, summarizeAmounts(quarter.previous(4), reports)),
        growth(current, summarizeAmounts(quarter.previous(1), reports)));
  }

  private Growth growth(Quarter current, Quarter previous) {
    if (current.basis() == null || current.currency() == null || !current.basis().equals(previous.basis())
        || !current.currency().equals(previous.currency())) {
      return null;
    }
    return new Growth(percentChange(current.revenue(), previous.revenue()),
        percentChange(current.operatingProfit(), previous.operatingProfit()),
        percentChange(current.netIncome(), previous.netIncome()));
  }

  private String percentChange(String current, String previous) {
    if (current == null || previous == null) {
      return null;
    }
    BigDecimal base = new BigDecimal(previous);
    if (base.signum() == 0) {
      return null;
    }
    return new BigDecimal(current).subtract(base).movePointRight(2)
        .divide(base.abs(), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }

  private Quarter summarizeAmounts(ReportingQuarter quarter, Map<ReportingQuarter, List<StoredFiling>> reports) {
    Optional<StoredFiling> current = preferred(reports.getOrDefault(quarter, List.of()));
    Optional<StoredFiling> third = quarter.number() == 4
        ? preferred(reports.getOrDefault(quarter.previous(1), List.of())) : Optional.empty();
    String basis = current.map(StoredFiling::basis).orElse(null);

    Amount revenue = calculateAmount(EarningsAccount.REVENUE, quarter, current, third, basis);
    Amount operatingProfit = calculateAmount(EarningsAccount.OPERATING_PROFIT, quarter, current, third, basis);
    Amount netIncome = calculateAmount(EarningsAccount.NET_INCOME, quarter, current, third, basis);

    List<String> currencies = Stream.of(revenue, operatingProfit, netIncome)
        .filter(Objects::nonNull).map(Amount::currency).distinct().toList();
    String currency = currencies.size() == 1 ? currencies.getFirst() : null;

    String revenueValue = value(revenue, currency);
    String operatingProfitValue = value(operatingProfit, currency);
    String netIncomeValue = value(netIncome, currency);

    List<Source> sources = new ArrayList<>();
    current.map(StoredFiling::source).ifPresent(sources::add);
    boolean usedThird = quarter.number() == 4
        && (revenueValue != null || operatingProfitValue != null || netIncomeValue != null);
    if (usedThird) {
      third.map(StoredFiling::source).ifPresent(sources::add);
    }

    return new Quarter(quarter.fiscalYear(), quarter.number(), quarter.end().toString(), basis, currency,
        revenueValue, operatingProfitValue, netIncomeValue, List.copyOf(sources), null, null);
  }

  private Amount calculateAmount(EarningsAccount account, ReportingQuarter quarter,
      Optional<StoredFiling> current, Optional<StoredFiling> third, String basis) {
    if (basis == null) {
      return null;
    }
    StoredFiling filing = current.orElseThrow();
    EarningsRow row = account.select(filing.rows(basis), AmountKind.CURRENT);
    if (row == null) {
      return null;
    }
    BigDecimal amount = row.currentAmount();
    String currency = row.currency();
    if (quarter.number() == 4) {
      // 4분기 단독 실적 = 연간(사업보고서) - 3분기 누적(3분기보고서)
      // 방어 로직: 3분기 보고서가 연간보고서보다 나중에 정정됐으면 비교 가능성 불확실로 제외
      Optional<StoredFiling> comparable = third.filter(value -> value.receiptNo().compareTo(filing.receiptNo()) < 0);
      EarningsRow cumulative = comparable
          .map(value -> account.select(value.rows(basis), AmountKind.CUMULATIVE))
          .orElse(null);
      if (cumulative == null || !currency.equals(cumulative.currency())
          || !EarningsAccount.sameAccount(row, cumulative)) {
        return null;
      }
      amount = amount.subtract(cumulative.cumulativeAmount());
    }
    return new Amount(amount.stripTrailingZeros().toPlainString(), currency);
  }

  private String value(Amount amount, String currency) {
    return amount != null && amount.currency().equals(currency) ? amount.value() : null;
  }

  private Optional<StoredFiling> preferred(List<StoredFiling> candidates) {
    Map<String, String> titles = candidates.stream()
        .collect(Collectors.toMap(StoredFiling::receiptNo, StoredFiling::title));
    return candidates.stream()
        .filter(filing -> ReportingQuarter.canUse(filing.receiptNo(), titles))
        .max(Comparator.comparing((StoredFiling filing) -> filing.basis() != null)
            .thenComparing(StoredFiling::receiptNo));
  }

  private record Amount(String value, String currency) {
  }

  public record StoredFiling(String receiptNo, String title, LocalDate receivedDate,
      Map<String, List<EarningsRow>> rowsByBasis) {
    public List<EarningsRow> rows(String basis) {
      return rowsByBasis.getOrDefault(basis, List.of());
    }

    public String basis() {
      return !rows("CFS").isEmpty() ? "CFS" : !rows("OFS").isEmpty() ? "OFS" : null;
    }

    public boolean hasAmounts(boolean hasConsolidated, boolean needsCumulative) {
      String basis = hasConsolidated ? "CFS" : basis();
      return basis != null && EarningsAccount.hasAmounts(rows(basis), needsCumulative);
    }

    public Source source() {
      return new Source(receiptNo, receivedDate, DART_URL + receiptNo);
    }
  }
}
