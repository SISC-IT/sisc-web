package org.sejongisc.backend.disclosure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse.Metric;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyDetailResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyListItem;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyListResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Quarter;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Source;
import org.sejongisc.backend.disclosure.entity.DisclosureCardCategory;
import org.sejongisc.backend.disclosure.entity.DisclosureCardNews;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.sejongisc.backend.disclosure.entity.DisclosureFiling;
import org.sejongisc.backend.disclosure.entity.DisclosureFinancialMetric;
import org.sejongisc.backend.disclosure.entity.DisclosureUserRead;
import org.sejongisc.backend.disclosure.repository.DisclosureCardNewsRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureCardNewsRepository.CompanyEventSummary;
import org.sejongisc.backend.disclosure.repository.DisclosureCompanyRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFilingRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFinancialMetricRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureUserReadRepository;
import org.sejongisc.backend.disclosure.service.enums.EarningsAccount;
import org.sejongisc.backend.disclosure.service.enums.EarningsRow;
import org.sejongisc.backend.disclosure.service.enums.EarningsRow.AmountKind;
import org.sejongisc.backend.disclosure.service.enums.ReportingQuarter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisclosureQueryService {
  private static final String DART_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";
  private static final TypeReference<List<Metric>> METRICS_TYPE = new TypeReference<>() {};
  private static final TypeReference<List<String>> TAGS_TYPE = new TypeReference<>() {};

  private final DisclosureCompanyRepository companyRepository;
  private final DisclosureFilingRepository filingRepository;
  private final DisclosureFinancialMetricRepository metricRepository;
  private final DisclosureCardNewsRepository cardRepository;
  private final DisclosureUserReadRepository userReadRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public CompanyListResponse listCompanies(UUID userId, String keyword, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 200);
    String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

    Page<DisclosureCompany> companies = companyRepository.findTrackedCompanies(
        normalizedKeyword, PageRequest.of(safePage, safeSize));
    List<UUID> companyIds = companies.getContent().stream()
        .map(DisclosureCompany::getCompanyId)
        .toList();

    Map<UUID, CompanyEventSummary> summaryByCompany = companyIds.isEmpty()
        ? Map.of()
        : cardRepository.summarizeByCompanyIds(companyIds).stream()
            .collect(Collectors.toMap(CompanyEventSummary::getCompanyId, Function.identity()));

    Map<UUID, LocalDateTime> viewedAtByCompany = (userId == null || companyIds.isEmpty())
        ? Map.of()
        : userReadRepository.findAllByUserIdAndCompany_CompanyIdIn(userId, companyIds).stream()
            .collect(Collectors.toMap(
                read -> read.getCompany().getCompanyId(),
                DisclosureUserRead::getLastViewedAt));

    List<CompanyListItem> items = companies.getContent().stream()
        .map(company -> {
          CompanyEventSummary summary = summaryByCompany.get(company.getCompanyId());
          LocalDateTime latestAt = summary == null ? null : summary.getLatestCollectedAt();
          LocalDate latestDate = summary == null ? null : summary.getLatestPublishedDate();
          long eventCount = summary == null ? 0L : summary.getEventCount();
          LocalDateTime lastViewedAt = viewedAtByCompany.get(company.getCompanyId());
          boolean hasUnread = latestAt != null
              && (lastViewedAt == null || latestAt.isAfter(lastViewedAt));
          return new CompanyListItem(
              company.getCorpCode(),
              company.getCompanyName(),
              company.getStockCode(),
              toMarketName(company.getCorporationClass()),
              company.isTrackCapex(),
              latestDate,
              eventCount,
              hasUnread);
        })
        .toList();

    return new CompanyListResponse(
        items,
        companies.getTotalElements(),
        companies.getNumber(),
        companies.getSize(),
        companies.getTotalPages());
  }

  @Transactional
  public Optional<CompanyDetailResponse> companyDetail(UUID userId, String corpCode,
      DisclosureCardCategory eventType) {
    Optional<DisclosureCompany> companyOpt = companyRepository.findByCorpCode(corpCode);
    if (companyOpt.isEmpty()) {
      return Optional.empty();
    }
    DisclosureCompany company = companyOpt.get();

    if (userId != null) {
      LocalDateTime now = LocalDateTime.now();
      userReadRepository.findByUserIdAndCompany_CompanyId(userId, company.getCompanyId())
          .ifPresentOrElse(
              read -> read.markViewed(now),
              () -> userReadRepository.save(new DisclosureUserRead(userId, company, now)));
    }

    List<Quarter> earningsTrend = allQuarters(corpCode);
    Quarter latestQuarter = earningsTrend.isEmpty() ? null : earningsTrend.getLast();

    List<DisclosureCardNews> rawEvents = eventType == null
        ? cardRepository.findAllByCompany_CompanyIdOrderByPublishedDateDescCreatedDateDesc(
            company.getCompanyId())
        : cardRepository.findAllByCompany_CompanyIdAndCategoryOrderByPublishedDateDescCreatedDateDesc(
            company.getCompanyId(), eventType);

    List<DisclosureCardResponse> events = rawEvents.stream()
        .map(this::toCardResponse)
        .toList();

    return Optional.of(new CompanyDetailResponse(
        company.getCorpCode(),
        company.getCompanyName(),
        company.getStockCode(),
        toMarketName(company.getCorporationClass()),
        company.getIndutyCode(),
        company.isTrackCapex(),
        latestQuarter,
        earningsTrend,
        events));
  }

  @Transactional(readOnly = true)
  public Optional<DisclosureEarningsResponse> latestEarnings(String corpCode, LocalDate through) {
    return companyRepository.findByCorpCode(corpCode)
        .map(company -> toEarningsResponse(company, storedReports(corpCode, through)));
  }

  @Transactional(readOnly = true)
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

  @Transactional(readOnly = true)
  Map<ReportingQuarter, List<StoredFiling>> storedReports(String corpCode, LocalDate through) {
    List<DisclosureFiling> allFilings = filingRepository
        .findAllByCompany_CorpCodeAndReportCodeIsNotNull(corpCode)
        .stream().filter(filing -> through == null || !filing.getReceivedDate().isAfter(through))
        .toList();
    if (allFilings.isEmpty()) {
      return Map.of();
    }

    Map<UUID, Map<String, List<EarningsRow>>> rowsByFiling = new HashMap<>();
    List<UUID> filingIds = allFilings.stream().map(DisclosureFiling::getFilingId).toList();
    for (var metric : metricRepository.findAllByFiling_FilingIdInAndMetricTypeIn(
        filingIds, EarningsAccount.METRIC_TYPES)) {
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

  private DisclosureEarningsResponse toEarningsResponse(DisclosureCompany company,
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
      // 4분기 단독 실적 = 연간(사업보고서) - 3분기 누적(3분기보고서)
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

  private DisclosureCardResponse toCardResponse(DisclosureCardNews entity) {
    var company = entity.getCompany();
    return new DisclosureCardResponse(
        entity.getCardId(),
        entity.getReceiptNo(),
        entity.getCategory().name(),
        entity.getCategory().displayName(),
        company.getCorpCode(),
        company.getCompanyName(),
        company.getStockCode(),
        toMarketName(entity.getMarketClass()),
        company.getIndutyCode(),
        entity.getReportTitle(),
        entity.getHeadline(),
        entity.getPublishedDate(),
        readJson(entity.getMetricsJson(), METRICS_TYPE),
        readJson(entity.getTagsJson(), TAGS_TYPE),
        entity.getDartUrl());
  }

  private static String toMarketName(String corpCls) {
    return switch (corpCls == null ? "" : corpCls) {
      case "Y" -> "KOSPI";
      case "K" -> "KOSDAQ";
      case "N" -> "KONEX";
      default -> "ETC";
    };
  }

  private <T> List<T> readJson(String json, TypeReference<List<T>> type) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, type);
    } catch (Exception ignored) {
      return List.of();
    }
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
