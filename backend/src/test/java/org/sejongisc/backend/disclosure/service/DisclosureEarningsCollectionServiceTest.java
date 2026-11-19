package org.sejongisc.backend.disclosure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.disclosure.client.OpenDartClient;
import org.sejongisc.backend.disclosure.client.OpenDartClient.DartCompany;
import org.sejongisc.backend.disclosure.client.OpenDartClient.DartDisclosurePage;
import org.sejongisc.backend.disclosure.client.OpenDartClient.DartFinancialResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Quarter;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Source;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.sejongisc.backend.disclosure.entity.DisclosureFiling;
import org.sejongisc.backend.disclosure.entity.DisclosureFinancialMetric;
import org.sejongisc.backend.disclosure.repository.DisclosureCompanyRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFilingRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFinancialMetricRepository;
import org.springframework.test.util.ReflectionTestUtils;

class DisclosureEarningsCollectionServiceTest {
  private static final String COMPANY = "00000001";
  private static final LocalDate THROUGH = LocalDate.of(2027, 4, 1);
  private static final String THIRD_2025 = "20251114000001";
  private static final String ANNUAL_2025 = "20260309000001";
  private static final String FIRST_2026 = "20260515000001";
  private static final String SECOND_2026 = "20260814000001";
  private static final String THIRD_2026 = "20261113000001";
  private static final String ANNUAL_2026 = "20270309000001";

  private final OpenDartClient client = mock(OpenDartClient.class);
  private final DisclosureCompanyRepository companies = mock(DisclosureCompanyRepository.class);
  private final DisclosureFilingRepository filings = mock(DisclosureFilingRepository.class);
  private final DisclosureFinancialMetricRepository metrics = mock(DisclosureFinancialMetricRepository.class);
  private final Map<String, DisclosureCompany> storedCompanies = new LinkedHashMap<>();
  private final Map<String, DisclosureFiling> storedFilings = new LinkedHashMap<>();
  private final List<DisclosureFinancialMetric> storedMetrics = new ArrayList<>();
  private final ObjectMapper mapper = new ObjectMapper();
  private final DisclosureEarningsQueryService query = new DisclosureEarningsQueryService(companies, filings, metrics, mapper);
  private final DisclosureEarningsCollectionService collection = new DisclosureEarningsCollectionService(client,
      new DisclosureCollectionService(companies, filings, metrics, mapper), query);

  @BeforeEach
  void setUpRepositories() {
    when(client.company(COMPANY)).thenReturn(new DartCompany("000", "12"));
    when(companies.findByCorpCode(COMPANY)).thenAnswer(invocation -> Optional.ofNullable(storedCompanies.get(COMPANY)));
    when(companies.findForUpdate(COMPANY)).thenAnswer(invocation -> Optional.ofNullable(storedCompanies.get(COMPANY)));
    when(companies.saveAndFlush(any(DisclosureCompany.class))).thenAnswer(invocation -> {
      DisclosureCompany company = invocation.getArgument(0);
      storedCompanies.put(company.getCorpCode(), company);
      return company;
    });
    when(filings.findByReceiptNo(anyString())).thenAnswer(invocation ->
        Optional.ofNullable(storedFilings.get(invocation.getArgument(0))));
    when(filings.save(any(DisclosureFiling.class))).thenAnswer(invocation -> {
      DisclosureFiling filing = invocation.getArgument(0);
      ReflectionTestUtils.setField(filing, "filingId", UUID.randomUUID());
      storedFilings.put(filing.getReceiptNo(), filing);
      return filing;
    });
    when(filings.findAllByCompany_CorpCodeAndReportCodeIsNotNull(COMPANY)).thenAnswer(invocation ->
        storedFilings.values().stream().filter(filing -> filing.getReportCode() != null).toList());
    when(metrics.save(any(DisclosureFinancialMetric.class))).thenAnswer(invocation -> {
      DisclosureFinancialMetric metric = invocation.getArgument(0);
      storedMetrics.add(metric);
      return metric;
    });
    when(metrics.findAllByFiling_FilingIdInAndMetricTypeIn(anyCollection(), anyCollection()))
        .thenAnswer(invocation -> List.copyOf(storedMetrics));
    when(metrics.existsByFiling_FilingIdAndMetricTypeAndBasisAndPeriodTypeAndAccountId(
        any(UUID.class), anyString(), anyString(), anyString(), any())).thenAnswer(invocation ->
        storedMetrics.stream().anyMatch(metric -> metric.getFiling().getFilingId().equals(invocation.getArgument(0))
            && metric.getMetricType().equals(invocation.getArgument(1))
            && metric.getBasis().equals(invocation.getArgument(2))
            && metric.getPeriodType().equals(invocation.getArgument(3))
            && Objects.equals(metric.getAccountId(), invocation.getArgument(4))));
  }

  @Test
  void loadsFourQuartersThenOnlyRetrievesTheNewQuarterAndKeepsHistory() {
    List<Map<String, String>> reports = List.of(
        filing(THIRD_2026, "분기보고서 (2026.09)", "연"),
        filing(SECOND_2026, "반기보고서 (2026.06)", "연"),
        filing(FIRST_2026, "분기보고서 (2026.03)", "연"),
        filing(ANNUAL_2025, "사업보고서 (2025.12)", "연"),
        filing(THIRD_2025, "분기보고서 (2025.09)", "연"));
    when(client.searchRegularReports(eq(COMPANY), any(), eq(THROUGH), eq(1), eq(100)))
        .thenReturn(page(1, 2, reports.subList(0, 3)));
    when(client.searchRegularReports(eq(COMPANY), any(), eq(THROUGH), eq(2), eq(100)))
        .thenReturn(page(2, 2, reports.subList(3, 5)));
    financial(2025, "11014", THIRD_2025, "CFS", "60", "200");
    financial(2025, "11011", ANNUAL_2025, "CFS", "300", null);
    financial(2026, "11013", FIRST_2026, "CFS", "0", null);
    financial(2026, "11012", SECOND_2026, "CFS", "-15", null);
    financial(2026, "11014", THIRD_2026, "CFS", "90", "210");

    var first = collection.collect(COMPANY, THROUGH);
    var quarters = first.earnings().orElseThrow().quarters();
    assertThat(quarters).extracting(Quarter::periodEnd)
        .containsExactly("2025-12", "2026-03", "2026-06", "2026-09");
    assertThat(quarters).extracting(Quarter::revenue).containsExactly("100", "0", "-15", "90");
    assertThat(quarters.getFirst().sources()).extracting(Source::receiptNo)
        .containsExactly(ANNUAL_2025, THIRD_2025);
    assertThat(first.pendingPeriods()).isEmpty();
    assertThat(storedFilings).containsKey(THIRD_2025);
    assertThat(storedMetrics).allMatch(metric -> !metric.getAccountId().contains("Assets"));

    clearInvocations(client);
    var second = collection.collect(COMPANY, THROUGH);
    assertThat(second.counts()).isEqualTo(new DisclosureCollectionService.Counts(0, 0));
    verify(client, never()).financialStatements(anyString(), anyInt(), anyString(), anyString());

    when(client.searchRegularReports(eq(COMPANY), any(), eq(THROUGH), eq(1), eq(100)))
        .thenReturn(page(1, 1, java.util.stream.Stream.concat(
            java.util.stream.Stream.of(filing(ANNUAL_2026, "사업보고서 (2026.12)", "연")), reports.stream()).toList()));
    financial(2026, "11011", ANNUAL_2026, "CFS", "400", null);
    var updated = collection.collect(COMPANY, THROUGH);
    assertThat(updated.counts()).isEqualTo(new DisclosureCollectionService.Counts(1, 3));
    assertThat(updated.earnings().orElseThrow().quarters()).extracting(Quarter::revenue)
        .containsExactly("0", "-15", "90", "190");
    assertThat(storedFilings).containsKeys(THIRD_2025, ANNUAL_2025, ANNUAL_2026);
    assertThat(query.latest(COMPANY, LocalDate.of(2026, 12, 31)).orElseThrow().quarters().getLast().periodEnd())
        .isEqualTo("2026-09");
    verify(client).financialStatements(COMPANY, 2026, "11011", "CFS");
    verify(client, times(1)).financialStatements(anyString(), anyInt(), anyString(), anyString());
  }

  @Test
  void leavesConsolidatedReportPendingUntilTheFinancialApiHasThatReceipt() {
    setReports(List.of(filing(THIRD_2026, "분기보고서 (2026.09)", "연")));
    when(client.financialStatements(COMPANY, 2026, "11014", "CFS"))
        .thenReturn(new DartFinancialResponse("013", null, null));
    var pending = collection.collect(COMPANY, THROUGH);
    assertThat(pending.pendingPeriods()).containsExactly("2026-09");
    assertThat(pending.earnings().orElseThrow().quarters().getLast().revenue()).isNull();
    verify(client, never()).financialStatements(COMPANY, 2026, "11014", "OFS");

    financial(2026, "11014", THIRD_2026, "CFS", "900", "2000");
    var received = collection.collect(COMPANY, THROUGH);
    assertThat(received.pendingPeriods()).isEmpty();
    assertThat(received.earnings().orElseThrow().quarters().getLast().revenue()).isEqualTo("900");
  }

  @Test
  void readsSeparateStatementsButDoesNotMixThemWithConsolidatedAnnualResults() {
    setReports(List.of(filing(ANNUAL_2025, "사업보고서 (2025.12)", "연"),
        filing(THIRD_2025, "분기보고서 (2025.09)", "")));
    when(client.financialStatements(COMPANY, 2025, "11014", "CFS"))
        .thenReturn(new DartFinancialResponse("013", null, null));
    financial(2025, "11014", THIRD_2025, "OFS", "10", "50");
    financial(2025, "11011", ANNUAL_2025, "CFS", "100", null);

    var quarters = collection.collect(COMPANY, THROUGH).earnings().orElseThrow().quarters();
    assertThat(quarters.get(2).basis()).isEqualTo("OFS");
    assertThat(quarters.getLast().basis()).isEqualTo("CFS");
    assertThat(quarters.getLast().revenue()).isNull();
    assertThat(quarters.getLast().sources()).extracting(Source::receiptNo).containsExactly(ANNUAL_2025);
  }

  @Test
  void correctionUpdatesTheQuarterAndDoesNotReuseItsNewCumulativeValueWithTheOlderAnnualReport() {
    setReports(List.of(filing(ANNUAL_2025, "사업보고서 (2025.12)", "연"),
        filing(THIRD_2025, "분기보고서 (2025.09)", "연")));
    financial(2025, "11014", THIRD_2025, "CFS", "30", "50");
    financial(2025, "11011", ANNUAL_2025, "CFS", "100", null);
    assertThat(collection.collect(COMPANY, THROUGH).earnings().orElseThrow().quarters().getLast().revenue())
        .isEqualTo("50");

    String correction = "20260415000001";
    setReports(List.of(filing(correction, "[기재정정]분기보고서 (2025.09)", "연"),
        filing(ANNUAL_2025, "사업보고서 (2025.12)", "연"),
        filing(THIRD_2025, "분기보고서 (2025.09)", "연정")));
    financial(2025, "11014", correction, "CFS", "40", "60");
    var updated = collection.collect(COMPANY, THROUGH).earnings().orElseThrow().quarters();
    assertThat(updated.get(2).revenue()).isEqualTo("40");
    assertThat(updated.getLast().revenue()).isNull();
    assertThat(updated.getLast().sources()).extracting(Source::receiptNo).containsExactly(ANNUAL_2025);
    assertThat(storedFilings).containsKeys(THIRD_2025, correction);
  }

  @Test
  void usesTheCompanyFiscalClosingMonthAndDoesNotTreatAnIncompletePageAsSuccess() {
    assertThat(ReportingQuarter.fromTitle("분기보고서 (2025.06)", 3)).hasValueSatisfying(quarter -> {
      assertThat(quarter.fiscalYear()).isEqualTo(2026);
      assertThat(quarter.number()).isEqualTo(1);
      assertThat(quarter.previous(1).end()).isEqualTo(YearMonth.of(2025, 3));
    });
    when(client.searchRegularReports(eq(COMPANY), any(), eq(THROUGH), eq(1), eq(100)))
        .thenReturn(page(1, 2, List.of(filing(THIRD_2026, "분기보고서 (2026.09)", "연"))));
    when(client.searchRegularReports(eq(COMPANY), any(), eq(THROUGH), eq(2), eq(100)))
        .thenReturn(new DartDisclosurePage("013", null, null, null, null, null, null));
    assertThatThrownBy(() -> collection.collect(COMPANY, THROUGH)).isInstanceOf(IllegalStateException.class);
    assertThat(storedFilings).isEmpty();
  }

  private void setReports(List<Map<String, String>> reports) {
    when(client.searchRegularReports(eq(COMPANY), any(), eq(THROUGH), eq(1), eq(100)))
        .thenReturn(page(1, 1, reports));
  }

  private static DartDisclosurePage page(int page, int totalPages, List<Map<String, String>> rows) {
    return new DartDisclosurePage("000", null, page, 100, rows.size(), totalPages, rows);
  }

  private static Map<String, String> filing(String receipt, String title, String remarks) {
    return Map.of("rcept_no", receipt, "corp_code", COMPANY, "corp_name", "Fixture Company",
        "report_nm", title, "rcept_dt", receipt.substring(0, 8), "rm", remarks);
  }

  private void financial(int year, String code, String receipt, String basis, String value, String cumulative) {
    var rows = new ArrayList<Map<String, String>>();
    for (String account : List.of("ifrs-full_Revenue", "dart_OperatingIncomeLoss", "ifrs-full_ProfitLoss")) {
      var row = new LinkedHashMap<>(Map.of("rcept_no", receipt, "corp_code", COMPANY,
          "bsns_year", Integer.toString(year), "reprt_code", code, "account_id", account,
          "account_nm", account, "sj_div", "IS", "currency", "KRW", "thstrm_amount", value));
      if (cumulative != null) {
        row.put("thstrm_add_amount", cumulative);
      }
      rows.add(row);
    }
    var balanceSheet = new LinkedHashMap<>(rows.getFirst());
    balanceSheet.put("account_id", "ifrs-full_Assets");
    balanceSheet.put("account_nm", "매출액");
    balanceSheet.put("sj_div", "BS");
    rows.add(balanceSheet);
    when(client.financialStatements(COMPANY, year, code, basis))
        .thenReturn(new DartFinancialResponse("000", null, rows));
  }
}
