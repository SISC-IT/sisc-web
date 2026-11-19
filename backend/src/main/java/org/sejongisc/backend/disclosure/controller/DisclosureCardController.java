package org.sejongisc.backend.disclosure.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse.PageResponse;
import org.sejongisc.backend.disclosure.entity.DisclosureCardCategory;
import org.sejongisc.backend.disclosure.service.DisclosureCardQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/disclosures/cards")
@RequiredArgsConstructor
public class DisclosureCardController {
  private final DisclosureCardQueryService queryService;

  @GetMapping
  public ResponseEntity<PageResponse> list(
      @RequestParam(value = "category", required = false) String category,
      @RequestParam(value = "q", required = false) String keyword,
      @RequestParam(value = "sort", defaultValue = "latest") String sort,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "12") int size) {
    DisclosureCardCategory parsedCategory = parseCategory(category);
    return ResponseEntity.ok(queryService.list(parsedCategory, keyword, sort, page, size));
  }

  @GetMapping("/{cardId}")
  public ResponseEntity<DisclosureCardResponse> detail(@PathVariable("cardId") UUID cardId) {
    return ResponseEntity.of(queryService.findById(cardId));
  }

  private DisclosureCardCategory parseCategory(String raw) {
    if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) {
      return null;
    }
    try {
      return DisclosureCardCategory.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new CustomException(ErrorCode.INVALID_INPUT);
    }
  }
}
