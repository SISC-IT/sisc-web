package org.sejongisc.backend.disclosure.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse.Metric;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyDetailResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyListItem;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyListResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Quarter;
import org.sejongisc.backend.disclosure.entity.DisclosureCardCategory;
import org.sejongisc.backend.disclosure.entity.DisclosureCardNews;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.sejongisc.backend.disclosure.entity.DisclosureUserRead;
import org.sejongisc.backend.disclosure.repository.DisclosureCardNewsRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureCardNewsRepository.CompanyEventSummary;
import org.sejongisc.backend.disclosure.repository.DisclosureCompanyRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureUserReadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisclosureCardQueryService {
  private static final TypeReference<List<Metric>> METRICS_TYPE = new TypeReference<>() {};
  private static final TypeReference<List<String>> TAGS_TYPE = new TypeReference<>() {};

  private final DisclosureCompanyRepository companyRepository;
  private final DisclosureCardNewsRepository cardRepository;
  private final DisclosureUserReadRepository userReadRepository;
  private final DisclosureEarningsQueryService earningsQueryService;
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

    // 사용자별 기업 공시 확인 시각 갱신 (빨간 점 해제)
    if (userId != null) {
      LocalDateTime now = LocalDateTime.now();
      userReadRepository.findByUserIdAndCompany_CompanyId(userId, company.getCompanyId())
          .ifPresentOrElse(
              read -> read.markViewed(now),
              () -> userReadRepository.save(new DisclosureUserRead(userId, company, now)));
    }

    // SSOT: 정기 실적은 disclosure_financial_metric에서 직접 조회
    List<Quarter> earningsTrend = earningsQueryService.allQuarters(corpCode);
    Quarter latestQuarter = earningsTrend.isEmpty() ? null : earningsTrend.getLast();

    // SSOT: 이벤트 공시(수주·주주환원·CAPEX·잠정실적)는 disclosure_card_news에서 조회
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
}
