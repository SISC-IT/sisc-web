package org.sejongisc.backend.disclosure.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse.Metric;
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse.PageResponse;
import org.sejongisc.backend.disclosure.entity.DisclosureCardCategory;
import org.sejongisc.backend.disclosure.entity.DisclosureCardNews;
import org.sejongisc.backend.disclosure.repository.DisclosureCardNewsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisclosureCardQueryService {
  private static final TypeReference<List<Metric>> METRICS_TYPE = new TypeReference<>() {};
  private static final TypeReference<List<String>> TAGS_TYPE = new TypeReference<>() {};

  private final DisclosureCardNewsRepository cardRepository;
  private final ObjectMapper objectMapper;

  public PageResponse list(DisclosureCardCategory category, String keyword, String sort,
      int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 50);
    Sort order = "oldest".equalsIgnoreCase(sort)
        ? Sort.by(Sort.Direction.ASC, "publishedDate", "createdDate")
        : Sort.by(Sort.Direction.DESC, "publishedDate", "createdDate");
    String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

    Page<DisclosureCardNews> result = cardRepository.search(
        category, normalizedKeyword, PageRequest.of(safePage, safeSize, order));

    return new PageResponse(
        result.getContent().stream().map(this::toResponse).toList(),
        result.getTotalElements(),
        result.getNumber(),
        result.getSize(),
        result.getTotalPages());
  }

  public Optional<DisclosureCardResponse> findById(UUID cardId) {
    return cardRepository.findWithCompanyByCardId(cardId).map(this::toResponse);
  }

  private DisclosureCardResponse toResponse(DisclosureCardNews entity) {
    var company = entity.getCompany();
    String market = switch (entity.getMarketClass() == null ? "" : entity.getMarketClass()) {
      case "Y" -> "KOSPI";
      case "K" -> "KOSDAQ";
      case "N" -> "KONEX";
      default -> "ETC";
    };
    return new DisclosureCardResponse(
        entity.getCardId(),
        entity.getReceiptNo(),
        entity.getCategory().name(),
        entity.getCategory().displayName(),
        company.getCorpCode(),
        company.getCompanyName(),
        company.getStockCode(),
        market,
        company.getIndutyCode(),
        entity.getReportTitle(),
        entity.getHeadline(),
        entity.getPublishedDate(),
        readJson(entity.getMetricsJson(), METRICS_TYPE),
        readJson(entity.getTagsJson(), TAGS_TYPE),
        entity.getDartUrl());
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
