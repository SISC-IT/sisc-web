package org.sejongisc.backend.disclosure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.disclosure.client.OpenDartClient;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.sejongisc.backend.disclosure.entity.DisclosureFiling;
import org.sejongisc.backend.disclosure.repository.DisclosureCompanyRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFilingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisclosureCollectionService {
  private static final DateTimeFormatter DART_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private final OpenDartClient client;
  private final DisclosureCompanyRepository companyRepository;
  private final DisclosureFilingRepository filingRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public int collect(String corpCode, LocalDate from, LocalDate to, int maxPages) {
    int inserted = 0;
    for (int page = 1; page <= Math.max(1, maxPages); page++) {
      OpenDartClient.DartDisclosurePage result = client.search(corpCode, from, to, page, 100);
      if (result == null || result.status() == null) {
        throw new IllegalStateException("OpenDART returned an empty response");
      }
      if ("013".equals(result.status())) break;
      if (!"000".equals(result.status())) {
        throw new IllegalStateException("OpenDART error " + result.status() + ": " + result.message());
      }
      if (result.list() == null || result.list().isEmpty()) break;
      for (Map<String, String> item : result.list()) {
        String receiptNo = item.get("rcept_no");
        if (receiptNo == null || receiptNo.isBlank() || filingRepository.existsByReceiptNo(receiptNo)) continue;
        DisclosureCompany company = companyRepository.findByCorpCode(item.get("corp_code"))
            .orElseGet(() -> companyRepository.save(new DisclosureCompany(
                item.get("corp_code"), blankToNull(item.get("stock_code")),
                item.get("corp_name"), blankToNull(item.get("corp_cls")))));
        filingRepository.save(toEntity(company, item));
        inserted++;
      }
      if (result.total_page() == null || page >= result.total_page()) break;
    }
    return inserted;
  }

  private DisclosureFiling toEntity(DisclosureCompany company, Map<String, String> item) {
    try {
      return new DisclosureFiling(company, item.get("rcept_no"), item.get("report_nm"),
          blankToNull(item.get("flr_nm")), LocalDate.parse(item.get("rcept_dt"), DART_DATE),
          blankToNull(item.get("rm")), objectMapper.writeValueAsString(item), LocalDateTime.now());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not preserve OpenDART disclosure payload", e);
    }
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
