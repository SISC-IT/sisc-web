package org.sejongisc.backend.disclosure.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyDetailResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyListResponse;
import org.sejongisc.backend.disclosure.entity.DisclosureCardCategory;
import org.sejongisc.backend.disclosure.service.DisclosureQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/disclosures/companies")
@RequiredArgsConstructor
public class DisclosureController {
  private final DisclosureQueryService queryService;

  //1. 리스트 조회
  @GetMapping
  public ResponseEntity<CompanyListResponse> list(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(value = "q", required = false) String keyword,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "200") int size) {
    UUID userId = userDetails == null ? null : userDetails.getUserId();
    return ResponseEntity.ok(queryService.listCompanies(userId, keyword, page, size));
  }

  //2. 상세 정보 조회
  @GetMapping("/{corpCode}")
  public ResponseEntity<CompanyDetailResponse> detail(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable("corpCode") String corpCode,
      @RequestParam(value = "type", required = false) String eventType) {
    if (!corpCode.matches("[0-9]{8}")) {
      throw new CustomException(ErrorCode.INVALID_INPUT);
    }
    UUID userId = userDetails == null ? null : userDetails.getUserId();
    DisclosureCardCategory parsedType = parseCategory(eventType);
    return ResponseEntity.of(queryService.companyDetail(userId, corpCode, parsedType));
  }

  //parsing
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
