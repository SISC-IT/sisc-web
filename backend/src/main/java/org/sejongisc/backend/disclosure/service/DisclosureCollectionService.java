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
import org.sejongisc.backend.disclosure.entity.DisclosureFinancialMetric;
import org.sejongisc.backend.disclosure.repository.DisclosureCompanyRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFilingRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFinancialMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisclosureCollectionService {
  private static final DateTimeFormatter DART_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private final OpenDartClient client;
  private final DisclosureCompanyRepository companyRepository;
  private final DisclosureFilingRepository filingRepository;
  private final DisclosureFinancialMetricRepository metricRepository;
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

  @Transactional
  public int collectFinancialMetrics(String corpCode, int businessYear, String reportCode,
      String basis) {
    OpenDartClient.DartFinancialResponse response =
        client.financialStatements(corpCode, businessYear, reportCode, basis);
    if (response == null || response.status() == null) {
      throw new IllegalStateException("OpenDART returned an empty financial response");
    }
    if ("013".equals(response.status())) return 0;
    if (!"000".equals(response.status())) {
      throw new IllegalStateException("OpenDART financial error " + response.status()
          + ": " + response.message());
    }
    if (response.list() == null) return 0;

    int inserted = 0;
    for (Map<String, String> row : response.list()) {
      String receiptNo = row.get("rcept_no");
      if (receiptNo == null) continue;
      DisclosureFiling filing = filingRepository.findByReceiptNo(receiptNo).orElse(null);
      if (filing == null) continue;
      filing.setFinancialReport(businessYear, reportCode);
      String currentPeriodType = "11011".equals(reportCode) ? "ANNUAL" : "CURRENT_PERIOD";
      inserted += saveMetric(filing, row, basis, currentPeriodType, "thstrm_amount");
      inserted += saveMetric(filing, row, basis, "YEAR_TO_DATE", "thstrm_add_amount");
    }
    filingRepository.flush();
    return inserted;
  }

  private int saveMetric(DisclosureFiling filing, Map<String, String> row, String basis,
      String periodType, String amountField) {
    String amountValue = row.get(amountField);
    if (amountValue == null || amountValue.isBlank() || "-".equals(amountValue.trim())) return 0;
    String accountName = row.getOrDefault("account_nm", "");
    String metricType = metricType(row.get("account_id"), accountName);
    String accountId = blankToNull(row.get("account_id"));
    if (metricRepository.existsByFiling_FilingIdAndMetricTypeAndBasisAndPeriodTypeAndAccountId(
        filing.getFilingId(), metricType, basis, periodType, accountId)) return 0;
    try {
      DisclosureFinancialMetric metric = new DisclosureFinancialMetric(filing, metricType,
          accountId, accountName, basis, periodType,
          new java.math.BigDecimal(amountValue.replace(",", "").trim()),
          blankToNull(row.get("currency")), objectMapper.writeValueAsString(row));
      metricRepository.save(metric);
      return 1;
    } catch (NumberFormatException | JsonProcessingException e) {
      throw new IllegalStateException("Invalid OpenDART financial metric: " + accountName, e);
    }
  }

  private String metricType(String accountId, String accountName) {
    String id = accountId == null ? "" : accountId.toLowerCase();
    String name = accountName.replace(" ", "");
    if ("ifrs-full_revenue".equals(id) || "dart_revenue".equals(id)
        || "매출액".equals(name) || "수익(매출액)".equals(name)) return "REVENUE";
    if ("dart_operatingincomeloss".equals(id) || "영업이익".equals(name)) return "OPERATING_PROFIT";
    if ("ifrs-full_profitloss".equals(id) || "당기순이익".equals(name)
        || "분기순이익".equals(name)) return "NET_INCOME";
    return "OTHER";
  }

  private DisclosureFiling toEntity(DisclosureCompany company, Map<String, String> item) {
    try {
      return new DisclosureFiling(company, item.get("rcept_no"), item.get("report_nm"),
          blankToNull(item.get("flr_nm")), LocalDate.parse(item.get("rcept_dt"), DART_DATE),
          null, null, blankToNull(item.get("rm")), objectMapper.writeValueAsString(item),
          LocalDateTime.now());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not preserve OpenDART disclosure payload", e);
    }
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
