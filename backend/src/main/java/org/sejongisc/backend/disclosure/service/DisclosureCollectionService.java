package org.sejongisc.backend.disclosure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.sejongisc.backend.disclosure.entity.DisclosureFiling;
import org.sejongisc.backend.disclosure.entity.DisclosureFinancialMetric;
import org.sejongisc.backend.disclosure.repository.DisclosureCompanyRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFilingRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFinancialMetricRepository;
import org.sejongisc.backend.disclosure.service.DisclosureEarningsCollectionService.FinancialReport;
import org.sejongisc.backend.disclosure.service.DisclosureEarningsCollectionService.RegularReport;
import org.sejongisc.backend.disclosure.service.EarningsRow.AmountKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisclosureCollectionService {
  private final DisclosureCompanyRepository companyRepository;
  private final DisclosureFilingRepository filingRepository;
  private final DisclosureFinancialMetricRepository metricRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public Counts saveEarnings(String corpCode, int closingMonth, List<RegularReport> regularReports,
      List<FinancialReport> financialReports) {
    if (regularReports.isEmpty()) {
      return new Counts(0, 0);
    }
    DisclosureCompany company = upsertCompany(corpCode, closingMonth, regularReports.getFirst());
    Map<String, DisclosureFiling> filingsByReceipt = new HashMap<>();
    int filingsInserted = upsertFilings(corpCode, company, regularReports, filingsByReceipt);
    int metricsInserted = saveFinancialMetrics(financialReports, filingsByReceipt);
    return new Counts(filingsInserted, metricsInserted);
  }

  private DisclosureCompany upsertCompany(String corpCode, int closingMonth, RegularReport sample) {
    DisclosureCompany company = companyRepository.findForUpdate(corpCode).orElseGet(() ->
        companyRepository.saveAndFlush(new DisclosureCompany(
            corpCode, sample.stockCode(), sample.companyName(), sample.corporationClass(), closingMonth)));
    company.updateProfile(sample.stockCode(), sample.companyName(), sample.corporationClass(), closingMonth);
    return company;
  }

  private int upsertFilings(String corpCode, DisclosureCompany company, List<RegularReport> regularReports,
      Map<String, DisclosureFiling> byReceipt) {
    int inserted = 0;
    for (RegularReport report : regularReports) {
      var existing = filingRepository.findByReceiptNo(report.receiptNo());
      DisclosureFiling filing = existing.orElseGet(() -> filingRepository.save(toEntity(company, report)));
      if (!corpCode.equals(filing.getCompany().getCorpCode())) {
        throw new IllegalStateException("Disclosure receipt belongs to another company");
      }
      if (!Objects.equals(report.quarter().fiscalYear(), filing.getBusinessYear())
          || !report.quarter().reportCode().equals(filing.getReportCode())) {
        filing.setFinancialReport(report.quarter().fiscalYear(), report.quarter().reportCode());
      }
      byReceipt.put(report.receiptNo(), filing);
      if (existing.isEmpty()) {
        inserted++;
      }
    }
    return inserted;
  }

  private int saveFinancialMetrics(List<FinancialReport> financialReports,
      Map<String, DisclosureFiling> filingsByReceipt) {
    int inserted = 0;
    for (FinancialReport financial : financialReports) {
      DisclosureFiling filing = Objects.requireNonNull(filingsByReceipt.get(financial.receiptNo()),
          "Missing regular report for financial receipt: " + financial.receiptNo());
      filing.setFinancialReport(financial.businessYear(), financial.quarter().reportCode());
      String currentPeriodType = financial.quarter().number() == 4
          ? EarningsAccount.ANNUAL : EarningsAccount.CURRENT;

      for (EarningsAccount account : EarningsAccount.values()) {
        EarningsRow currentRow = account.select(financial.rows(), AmountKind.CURRENT);
        if (currentRow != null) {
          inserted += saveMetric(filing, currentRow, financial.basis(), currentPeriodType,
              AmountKind.CURRENT, account.name());
        }
        if (financial.quarter().number() == 3) {
          EarningsRow cumulativeRow = account.select(financial.rows(), AmountKind.CUMULATIVE);
          if (cumulativeRow != null) {
            inserted += saveMetric(filing, cumulativeRow, financial.basis(), EarningsAccount.YEAR_TO_DATE,
                AmountKind.CUMULATIVE, account.name());
          }
        }
      }
    }
    return inserted;
  }

  private int saveMetric(DisclosureFiling filing, EarningsRow row, String basis,
      String periodType, AmountKind kind, String metricType) {
    BigDecimal amount = row.amount(kind);
    if (amount == null) {
      return 0;
    }
    if (metricRepository.existsByFiling_FilingIdAndMetricTypeAndBasisAndPeriodTypeAndAccountId(
        filing.getFilingId(), metricType, basis, periodType, row.accountId())) {
      return 0;
    }
    try {
      DisclosureFinancialMetric metric = new DisclosureFinancialMetric(
          filing, metricType, row.accountId(), row.accountName(), basis, periodType,
          amount, row.currency(), objectMapper.writeValueAsString(row.rawPayload()));
      metricRepository.save(metric);
      return 1;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not preserve OpenDART financial metric: " + row.accountName(), e);
    }
  }

  private DisclosureFiling toEntity(DisclosureCompany company, RegularReport report) {
    try {
      return new DisclosureFiling(
          company, report.receiptNo(), report.title(), report.submitterName(),
          report.receivedDate(), null, null, report.remarks(),
          objectMapper.writeValueAsString(report.rawPayload()), LocalDateTime.now());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not preserve OpenDART disclosure payload", e);
    }
  }

  public record Counts(int filingsInserted, int metricsInserted) {
  }
}
