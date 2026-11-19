package org.sejongisc.backend.disclosure.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.disclosure.client.OpenDartClient;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse;
import org.sejongisc.backend.disclosure.service.DisclosureCollectionService.Counts;
import org.sejongisc.backend.disclosure.service.DisclosureEarningsQueryService.StoredFiling;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DisclosureEarningsCollectionService {
  private static final LocalDate XBRL_START = LocalDate.of(2015, 1, 1);
  private static final DateTimeFormatter DART_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final OpenDartClient client;
  private final DisclosureCollectionService storage;
  private final DisclosureEarningsQueryService query;

  public CollectionResult collect(String corpCode, LocalDate through) {
    if (!corpCode.matches("[0-9]{8}") || through.isBefore(XBRL_START)) {
      throw new IllegalArgumentException("Provide a DART company code and a date from 2015 onward");
    }

    int closingMonth = fetchClosingMonth(corpCode);
    TreeMap<ReportingQuarter, List<RegularReport>> available = fetchRegularReports(corpCode, through, closingMonth);
    if (available.isEmpty()) {
      return new CollectionResult(new Counts(0, 0), List.of(), Optional.empty());
    }

    Map<ReportingQuarter, List<StoredFiling>> previous = query.storedReports(corpCode, through);
    TargetQuarters targets = selectTargetQuarters(available, previous);
    List<RegularReport> selectedReports = targets.byQuarter().values().stream().flatMap(List::stream).toList();
    int filingsInserted = storage.saveReports(corpCode, closingMonth, selectedReports);
    FetchOutcome outcome = fetchMissingFinancials(corpCode, closingMonth, targets, previous);
    Counts counts = new Counts(filingsInserted, outcome.metricsInserted());
    return new CollectionResult(counts, List.copyOf(outcome.pendingPeriods()), query.latest(corpCode, through));
  }

  private int fetchClosingMonth(String corpCode) {
    var company = client.company(corpCode);
    requireSuccess(company == null ? null : company.status(), "company");
    if (company.acc_mt() == null || !company.acc_mt().matches("0[1-9]|1[0-2]")) {
      throw new IllegalStateException("OpenDART did not provide the fiscal closing month");
    }
    return Integer.parseInt(company.acc_mt());
  }

  private TargetQuarters selectTargetQuarters(TreeMap<ReportingQuarter, List<RegularReport>> available,
      Map<ReportingQuarter, List<StoredFiling>> previous) {
    ReportingQuarter earliest = available.lastKey().previous(3);
    ReportingQuarter firstTracked = previous.keySet().stream().min(Comparator.naturalOrder()).orElse(earliest);
    ReportingQuarter start = firstTracked.compareTo(earliest) < 0 ? firstTracked : earliest;

    var selected = new TreeMap<>(available.tailMap(start, true));
    Set<ReportingQuarter> cumulativePeriods = selected.keySet().stream()
        .filter(quarter -> quarter.number() == 4)
        .map(quarter -> quarter.previous(1))
        .collect(Collectors.toCollection(LinkedHashSet::new));

    for (ReportingQuarter quarter : cumulativePeriods) {
      if (available.containsKey(quarter)) {
        selected.put(quarter, available.get(quarter));
      }
    }
    return new TargetQuarters(selected, cumulativePeriods);
  }

  private FetchOutcome fetchMissingFinancials(String corpCode, int closingMonth, TargetQuarters targets,
      Map<ReportingQuarter, List<StoredFiling>> previous) {
    int metricsInserted = 0;
    Set<String> pending = new LinkedHashSet<>();

    for (var entry : targets.byQuarter().entrySet()) {
      ReportingQuarter quarter = entry.getKey();
      List<RegularReport> candidates = entry.getValue();
      Map<String, String> titles = candidates.stream()
          .collect(Collectors.toMap(RegularReport::receiptNo, RegularReport::title));

      if (candidates.stream().anyMatch(report -> !ReportingQuarter.canUse(report.receiptNo(), titles))) {
        pending.add(quarter.end().toString());
      }
      Optional<RegularReport> newest = candidates.stream()
          .filter(report -> ReportingQuarter.canUse(report.receiptNo(), titles))
          .max(Comparator.comparing(RegularReport::receiptNo));
      if (newest.isEmpty()) {
        continue;
      }

      RegularReport expected = newest.get();
      boolean needsCumulative = targets.cumulativePeriods().contains(quarter);
      boolean consolidated = expected.hasConsolidated();
      if (isAlreadyStored(previous.getOrDefault(quarter, List.of()), expected.receiptNo(),
          consolidated, needsCumulative)) {
        continue;
      }

      Optional<FinancialReport> result = fetchFinancialReport(corpCode, quarter, candidates, titles,
          consolidated, closingMonth != 12);
      if (result.isPresent()) {
        metricsInserted += storage.saveFinancialReport(corpCode, result.get());
      }
      if (result.filter(financial -> financial.receiptNo().equals(expected.receiptNo())
          && financial.hasAmounts(needsCumulative)).isEmpty()) {
        pending.add(quarter.end().toString());
      }
    }
    return new FetchOutcome(metricsInserted, pending);
  }

  private boolean isAlreadyStored(List<StoredFiling> stored, String receiptNo,
      boolean consolidated, boolean needsCumulative) {
    return stored.stream().anyMatch(filing ->
        filing.receiptNo().equals(receiptNo) && filing.hasAmounts(consolidated, needsCumulative));
  }

  private TreeMap<ReportingQuarter, List<RegularReport>> fetchRegularReports(String corpCode, LocalDate through,
      int closingMonth) {
    Map<String, RegularReport> reports = new LinkedHashMap<>();
    for (int page = 1; ; page++) {
      var response = client.searchRegularReports(corpCode, XBRL_START, through, page, 100);
      if (response != null && "013".equals(response.status()) && page == 1) {
        break;
      }
      requireSuccess(response == null ? null : response.status(), "regular reports");
      if (response.list() == null || response.list().isEmpty() || response.total_page() == null
          || response.total_page() < page || (response.page_no() != null && response.page_no() != page)) {
        throw new IllegalStateException("OpenDART returned an incomplete regular-report page");
      }
      for (Map<String, String> row : response.list()) {
        Optional<ReportingQuarter> quarter = ReportingQuarter.fromTitle(row.get("report_nm"), closingMonth);
        if (quarter.isPresent()) {
          String receipt = row.get("rcept_no");
          if (receipt == null || !receipt.matches("[0-9]{14}") || !corpCode.equals(row.get("corp_code"))) {
            throw new IllegalStateException("OpenDART returned invalid regular-report metadata");
          }
          reports.putIfAbsent(receipt, new RegularReport(quarter.get(), row));
        }
      }
      if (page == response.total_page()) {
        break;
      }
    }
    return reports.values().stream()
        .collect(Collectors.groupingBy(RegularReport::quarter, TreeMap::new, Collectors.toList()));
  }

  private Optional<FinancialReport> fetchFinancialReport(String corpCode, ReportingQuarter quarter,
      List<RegularReport> reports, Map<String, String> titles, boolean hasConsolidated, boolean nonDecember) {
    var byReceipt = reports.stream()
        .filter(report -> ReportingQuarter.canUse(report.receiptNo(), titles))
        .collect(Collectors.toMap(RegularReport::receiptNo, Function.identity()));
    List<String> bases = hasConsolidated ? List.of("CFS") : List.of("CFS", "OFS");
    List<Integer> years = nonDecember && quarter.fiscalYear() > XBRL_START.getYear()
        ? List.of(quarter.fiscalYear(), quarter.fiscalYear() - 1) : List.of(quarter.fiscalYear());

    for (String basis : bases) {
      for (int year : years) {
        var response = client.financialStatements(corpCode, year, quarter.reportCode(), basis);
        if (response != null && "013".equals(response.status())) {
          continue;
        }
        requireSuccess(response == null ? null : response.status(), "financial statements");
        if (response.list() == null || response.list().isEmpty()) {
          throw new IllegalStateException("OpenDART returned incomplete financial statements");
        }
        Map<String, List<EarningsRow>> matched = response.list().stream()
            .filter(row -> corpCode.equals(row.get("corp_code"))
                && quarter.reportCode().equals(row.get("reprt_code"))
                && Integer.toString(year).equals(row.get("bsns_year"))
                && row.get("rcept_no") != null && byReceipt.containsKey(row.get("rcept_no")))
            .collect(Collectors.groupingBy(
                row -> row.get("rcept_no"),
                Collectors.mapping(EarningsRow::fromDartRow, Collectors.toList())));
        Optional<String> receipt = matched.keySet().stream().max(Comparator.naturalOrder());
        if (receipt.isPresent()) {
          return Optional.of(new FinancialReport(quarter, receipt.get(), year, basis, matched.get(receipt.get())));
        }
      }
    }
    return Optional.empty();
  }

  private void requireSuccess(String status, String operation) {
    if (!"000".equals(status)) {
      String safeStatus = status != null && status.matches("[0-9]{3}") ? status : "missing/invalid";
      throw new IllegalStateException("OpenDART " + operation + " failed, status=" + safeStatus);
    }
  }

  private record TargetQuarters(TreeMap<ReportingQuarter, List<RegularReport>> byQuarter,
      Set<ReportingQuarter> cumulativePeriods) {
  }

  private record FetchOutcome(int metricsInserted, Set<String> pendingPeriods) {
  }

  public record CollectionResult(Counts counts, List<String> pendingPeriods,
      Optional<DisclosureEarningsResponse> earnings) {
  }

  record RegularReport(ReportingQuarter quarter, Map<String, String> rawPayload) {
    String receiptNo() { return rawPayload.get("rcept_no"); }
    String title() { return rawPayload.get("report_nm"); }
    String companyName() { return rawPayload.get("corp_name"); }
    String stockCode() { return blankToNull(rawPayload.get("stock_code")); }
    String corporationClass() { return blankToNull(rawPayload.get("corp_cls")); }
    String submitterName() { return blankToNull(rawPayload.get("flr_nm")); }
    String remarks() { return blankToNull(rawPayload.get("rm")); }
    LocalDate receivedDate() { return LocalDate.parse(rawPayload.get("rcept_dt"), DART_DATE); }

    boolean hasConsolidated() {
      String rm = remarks();
      return rm != null && rm.contains("연");
    }

    private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value;
    }
  }

  record FinancialReport(ReportingQuarter quarter, String receiptNo, int businessYear, String basis,
      List<EarningsRow> rows) {
    boolean hasAmounts(boolean needsCumulative) {
      return EarningsAccount.hasAmounts(rows, needsCumulative);
    }
  }
}
