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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyDetailResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyListItem;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyListResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Quarter;
import org.sejongisc.backend.disclosure.dto.DisclosureEventResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureEventResponse.Metric;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.sejongisc.backend.disclosure.entity.DisclosureEvent;
import org.sejongisc.backend.disclosure.entity.DisclosureEventCategory;
import org.sejongisc.backend.disclosure.entity.DisclosureFiling;
import org.sejongisc.backend.disclosure.entity.DisclosureFinancialMetric;
import org.sejongisc.backend.disclosure.entity.DisclosureUserRead;
import org.sejongisc.backend.disclosure.repository.DisclosureCompanyRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureEventRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureEventRepository.CompanyEventSummary;
import org.sejongisc.backend.disclosure.repository.DisclosureFilingRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFilingRepository.CompanyFilingSummary;
import org.sejongisc.backend.disclosure.repository.DisclosureFinancialMetricRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureUserReadRepository;
import org.sejongisc.backend.disclosure.service.enums.EarningsAccount;
import org.sejongisc.backend.disclosure.service.enums.EarningsRow;
import org.sejongisc.backend.disclosure.service.enums.ReportingQuarter;
import org.sejongisc.backend.disclosure.service.settings.QuarterlyEarningsCalculator;
import org.sejongisc.backend.disclosure.service.settings.QuarterlyEarningsCalculator.StoredFiling;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisclosureQueryService {
  private static final TypeReference<List<Metric>> METRICS_TYPE = new TypeReference<>() {};
  private static final TypeReference<List<String>> TAGS_TYPE = new TypeReference<>() {};

  private final DisclosureCompanyRepository companyRepository;
  private final DisclosureFilingRepository filingRepository;
  private final DisclosureFinancialMetricRepository metricRepository;
  private final DisclosureEventRepository eventRepository;
  private final DisclosureUserReadRepository userReadRepository;
  private final QuarterlyEarningsCalculator earningsCalculator;
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

    Map<UUID, CompanyEventSummary> eventSummaryByCompany = companyIds.isEmpty()
        ? Map.of()
        : eventRepository.summarizeByCompanyIds(companyIds).stream()
            .collect(Collectors.toMap(CompanyEventSummary::getCompanyId, Function.identity()));

    Map<UUID, LocalDateTime> latestFilingAtByCompany = companyIds.isEmpty()
        ? Map.of()
        : filingRepository.summarizeLatestCollectedAtByCompanyIds(companyIds).stream()
            .collect(Collectors.toMap(
                CompanyFilingSummary::getCompanyId,
                CompanyFilingSummary::getLatestCollectedAt));

    Map<UUID, LocalDateTime> viewedAtByCompany = (userId == null || companyIds.isEmpty())
        ? Map.of()
        : userReadRepository.findAllByUserIdAndCompany_CompanyIdIn(userId, companyIds).stream()
            .collect(Collectors.toMap(
                read -> read.getCompany().getCompanyId(),
                DisclosureUserRead::getLastViewedAt));

    List<CompanyListItem> items = companies.getContent().stream()
        .map(company -> {
          CompanyEventSummary eventSummary = eventSummaryByCompany.get(company.getCompanyId());
          LocalDateTime latestEventAt = eventSummary == null ? null : eventSummary.getLatestCollectedAt();
          LocalDateTime latestFilingAt = latestFilingAtByCompany.get(company.getCompanyId());
          LocalDateTime latestActivityAt = maxDateTime(latestEventAt, latestFilingAt);

          LocalDate latestDate = eventSummary == null ? null : eventSummary.getLatestPublishedDate();
          long eventCount = eventSummary == null ? 0L : eventSummary.getEventCount();
          LocalDateTime lastViewedAt = viewedAtByCompany.get(company.getCompanyId());
          boolean hasUnread = latestActivityAt != null
              && (lastViewedAt == null || latestActivityAt.isAfter(lastViewedAt));
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
      DisclosureEventCategory eventType) {
    Optional<DisclosureCompany> companyOpt = companyRepository.findByCorpCode(corpCode);
    if (companyOpt.isEmpty()) {
      return Optional.empty();
    }
    DisclosureCompany company = companyOpt.get();

    if (userId != null) {
      LocalDateTime now = LocalDateTime.now();
      userReadRepository.upsertLastViewedAt(UUID.randomUUID(), userId, company.getCompanyId(), now);
    }

    List<Quarter> earningsTrend = allQuarters(corpCode);
    Quarter latestQuarter = earningsTrend.isEmpty() ? null : earningsTrend.getLast();

    List<DisclosureEvent> rawEvents = eventType == null
        ? eventRepository.findAllByCompany_CompanyIdOrderByPublishedDateDescCreatedDateDesc(
            company.getCompanyId())
        : eventRepository.findAllByCompany_CompanyIdAndCategoryOrderByPublishedDateDescCreatedDateDesc(
            company.getCompanyId(), eventType);

    List<DisclosureEventResponse> events = rawEvents.stream()
        .map(this::toEventResponse)
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
        .map(company -> {
          Map<ReportingQuarter, List<StoredFiling>> reports = storedReports(corpCode, through);
          List<Quarter> quarters = reports.keySet().stream().max(Comparator.naturalOrder()).stream()
              .flatMap(latest -> latest.recentFour().stream()
                  .map(quarter -> earningsCalculator.summarize(quarter, reports)))
              .toList();
          return new DisclosureEarningsResponse(company.getCorpCode(), company.getCompanyName(),
              company.getStockCode(), quarters);
        });
  }

  @Transactional(readOnly = true)
  public List<Quarter> allQuarters(String corpCode) {
    Map<ReportingQuarter, List<StoredFiling>> reports = storedReports(corpCode, null);
    if (reports.isEmpty()) {
      return List.of();
    }
    return reports.keySet().stream()
        .sorted(Comparator.naturalOrder())
        .map(quarter -> earningsCalculator.summarize(quarter, reports))
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

  private DisclosureEventResponse toEventResponse(DisclosureEvent entity) {
    var company = entity.getCompany();
    return new DisclosureEventResponse(
        entity.getEventId(),
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

  private static LocalDateTime maxDateTime(LocalDateTime a, LocalDateTime b) {
    if (a == null) return b;
    if (b == null) return a;
    return a.isAfter(b) ? a : b;
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
}
