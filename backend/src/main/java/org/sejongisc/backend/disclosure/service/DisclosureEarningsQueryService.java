package org.sejongisc.backend.disclosure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Quarter;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Source;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.sejongisc.backend.disclosure.entity.DisclosureFiling;
import org.sejongisc.backend.disclosure.entity.DisclosureFinancialMetric;
import org.sejongisc.backend.disclosure.repository.DisclosureCompanyRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFilingRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFinancialMetricRepository;
import org.sejongisc.backend.disclosure.service.enums.EarningsAccount;
import org.sejongisc.backend.disclosure.service.enums.EarningsRow;
import org.sejongisc.backend.disclosure.service.enums.EarningsRow.AmountKind;
import org.sejongisc.backend.disclosure.service.enums.ReportingQuarter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisclosureEarningsQueryService {
  private static final String DART_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";

  private final DisclosureCompanyRepository companies;
  private final DisclosureFilingRepository filings;
  private final DisclosureFinancialMetricRepository metrics;
  private final ObjectMapper objectMapper;

  public Optional<DisclosureEarningsResponse> latest(String corpCode) {
    return latest(corpCode, null);
  }

  public List<Quarter> allQuarters(String corpCode) {
    Map<ReportingQuarter, List<StoredFiling>> reports = storedReports(corpCode, null);
    if (reports.isEmpty()) {
      return List.of();
    }
    return reports.keySet().stream()
        .sorted(Comparator.naturalOrder())
        .map(quarter -> summarize(quarter, reports))
        .toList();
  }

  Optional<DisclosureEarningsResponse> latest(String corpCode, LocalDate through) {
    return companies.findByCorpCode(corpCode)
        .map(company -> response(company, storedReports(corpCode, through)));
  }

  Map<ReportingQuarter, List<StoredFiling>> storedReports(String corpCode, LocalDate through) {
    List<DisclosureFiling> allFilings = filings.findAllByCompany_CorpCodeAndReportCodeIsNotNull(corpCode)
        .stream().filter(filing -> through == null || !filing.getReceivedDate().isAfter(through)).toList();
    if (allFilings.isEmpty()) {
      return Map.of();
    }

    Map<UUID, Map<String, List<EarningsRow>>> rowsByFiling = new HashMap<>();
    List<UUID> filingIds = allFilings.stream().map(DisclosureFiling::getFilingId).toList();
    for (var metric : metrics.findAllByFiling_FilingIdInAndMetricTypeIn(filingIds, EarningsAccount.METRIC_TYPES)) {
      toEarningsRow(metric).ifPresent(row -> rowsByFiling
          .computeIfAbsent(metric.getFiling().getFilingId(), id -> new HashMap<>())
          .computeIfAbsent(metric.getBasis(), basis -> new ArrayList<>())
          .add(row));
    }

    Map<ReportingQuarter, List<StoredFiling>> reports = new HashMap<>();
    for (DisclosureFiling filing : allFilings) {
      ReportingQuarter.fromFiling(filing).ifPresent(quarter ->
          reports.computeIfAbsent(quarter, ignored -> new ArrayList<>())
              .add(new StoredFiling(filing.getReceiptNo(), filing.getTitle(), filing.getReceivedDate(),
                  rowsByFiling.getOrDefault(filing.getFilingId(), Map.of()))));
    }
    return reports;
  }

  private Optional<EarningsRow> toEarningsRow(DisclosureFinancialMetric metric) {
    if (metric.getAmount() == null || metric.getCurrency() == null
        || !metric.getCurrency().matches("[A-Z]{3}")) {
      return Optional.empty();
    }
    String statementDivision = readStatementDivision(metric.getRawPayload());
    if (statementDivision == null) {
      return Optional.empty();
    }

    BigDecimal currentAmount = null;
    BigDecimal cumulativeAmount = null;
    switch (metric.getPeriodType()) {
      case EarningsAccount.CURRENT, EarningsAccount.ANNUAL -> currentAmount = metric.getAmount();
      case EarningsAccount.YEAR_TO_DATE -> cumulativeAmount = metric.getAmount();
      default -> {
        return Optional.empty();
      }
    }

    EarningsRow row = new EarningsRow(
        statementDivision, metric.getAccountId(), metric.getAccountName(),
        metric.getCurrency(), currentAmount, cumulativeAmount, null);
    return EarningsAccount.valueOf(metric.getMetricType()).rank(row) > 0
        ? Optional.of(row) : Optional.empty();
  }

  private String readStatementDivision(String rawPayload) {
    try {
      JsonNode node = objectMapper.readTree(rawPayload);
      JsonNode sjDiv = node == null ? null : node.get("sj_div");
      return sjDiv != null && sjDiv.isTextual() ? sjDiv.asText() : null;
    } catch (JsonProcessingException ignored) {
      return null;
    }
  }

  private DisclosureEarningsResponse response(DisclosureCompany company,
      Map<ReportingQuarter, List<StoredFiling>> reports) {
    List<Quarter> quarters = reports.keySet().stream().max(Comparator.naturalOrder()).stream()
        .flatMap(latest -> latest.recentFour().stream().map(quarter -> summarize(quarter, reports)))
        .toList();
    return new DisclosureEarningsResponse(company.getCorpCode(), company.getCompanyName(),
        company.getStockCode(), quarters);
  }

  private Quarter summarize(ReportingQuarter quarter, Map<ReportingQuarter, List<StoredFiling>> reports) {
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
        revenueValue, operatingProfitValue, netIncomeValue, List.copyOf(sources));
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
      // If the interim report changed after the annual filing, their comparability is unconfirmed.
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

  record StoredFiling(String receiptNo, String title, LocalDate receivedDate,
      Map<String, List<EarningsRow>> rowsByBasis) {
    List<EarningsRow> rows(String basis) {
      return rowsByBasis.getOrDefault(basis, List.of());
    }

    String basis() {
      return !rows("CFS").isEmpty() ? "CFS" : !rows("OFS").isEmpty() ? "OFS" : null;
    }

    boolean hasAmounts(boolean hasConsolidated, boolean needsCumulative) {
      String basis = hasConsolidated ? "CFS" : basis();
      return basis != null && EarningsAccount.hasAmounts(rows(basis), needsCumulative);
    }

    Source source() {
      return new Source(receiptNo, receivedDate, DART_URL + receiptNo);
    }
  }
}
