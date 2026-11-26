package org.sejongisc.backend.disclosure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.disclosure.client.OpenDartClient;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.sejongisc.backend.disclosure.entity.DisclosureFiling;
import org.sejongisc.backend.disclosure.entity.DisclosureFinancialMetric;
import org.sejongisc.backend.disclosure.repository.DisclosureCompanyRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFilingRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureFinancialMetricRepository;
import org.sejongisc.backend.disclosure.service.enums.EarningsAccount;
import org.sejongisc.backend.disclosure.service.enums.EarningsRow;
import org.sejongisc.backend.disclosure.service.enums.EarningsRow.AmountKind;
import org.sejongisc.backend.disclosure.service.enums.ReportingQuarter;
import org.sejongisc.backend.disclosure.service.settings.QuarterlyEarningsCalculator.StoredFiling;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
@RequiredArgsConstructor
public class DisclosureEarningsCollectionService {
  private static final LocalDate XBRL_START = LocalDate.of(2015, 1, 1);
  private static final DateTimeFormatter DART_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final OpenDartClient client;
  private final DisclosureCompanyRepository companyRepository;
  private final DisclosureFilingRepository filingRepository;
  private final DisclosureFinancialMetricRepository metricRepository;
  private final DisclosureQueryService query;
  private final TransactionOperations transactionOperations;
  private final ObjectMapper objectMapper;

  /*
  1~4번 메서드 실행
    1. 보고서 목록 받기
    2-1. 첫 시행일 경우, 최근 4개 분기
    2-2. 이미 저장된 분기면 건너띄기
    3. 데이터 받아오기
    4. DB 저장 (1. 기업 리스트 2. 보고서 목록 3. 보고서 세부사항)
   */
  public CollectionResult collect(String corpCode, LocalDate through) {
    // 0-1 방어 로직: 잘못된 기업 고유번호(8자리 숫자) 또는 XBRL 지원 이전(2015년 이전) 날짜 차단
    if (!corpCode.matches("[0-9]{8}") || through.isBefore(XBRL_START)) {
      throw new IllegalArgumentException("Provide a DART company code and a date from 2015 onward");
    }

    int closingMonth = fetchClosingMonth(corpCode);
    TreeMap<ReportingQuarter, List<RegularReport>> available = fetchRegularReports(corpCode, through, closingMonth);
    // 0-2 방어 로직: 수집된 정기보고서가 아예 없는 기업(신규상장 등)이면 빈 결과로 조기 종료
    if (available.isEmpty()) {
      return new CollectionResult(new Counts(0, 0), List.of(), Optional.empty());
    }

    Map<ReportingQuarter, List<StoredFiling>> previous = query.storedReports(corpCode, through);
    TargetQuarters targets = selectTargetQuarters(available, previous);
    List<RegularReport> selectedReports = targets.byQuarter().values().stream().flatMap(List::stream).toList();
    int filingsInserted = saveReports(corpCode, closingMonth, selectedReports);
    FetchOutcome outcome = fetchMissingFinancials(corpCode, closingMonth, targets, previous);
    Counts counts = new Counts(filingsInserted, outcome.metricsInserted());
    return new CollectionResult(counts, List.copyOf(outcome.pendingPeriods()), query.latestEarnings(corpCode, through));
  }

  //1번 메서드
  private int fetchClosingMonth(String corpCode) {
    var company = client.company(corpCode);
    // 1-1 방어 로직: OpenDART 기업개황 API 실패(000 외 상태) 차단
    requireSuccess(company == null ? null : company.status(), "company");
    // 1-2 방어 로직: 결산월(01~12)이 누락되거나 비정상 형식인 경우 차단
    if (company.acc_mt() == null || !company.acc_mt().matches("0[1-9]|1[0-2]")) {
      throw new IllegalStateException("OpenDART did not provide the fiscal closing month");
    }
    return Integer.parseInt(company.acc_mt());
  }

  private TreeMap<ReportingQuarter, List<RegularReport>> fetchRegularReports(String corpCode, LocalDate through,
      int closingMonth) {
    Map<String, RegularReport> reports = new LinkedHashMap<>();
    for (int page = 1; ; page++) {
      var response = client.searchRegularReports(corpCode, XBRL_START, through, page, 100);
      // 1-3 방어 로직: 첫 페이지에서 013(조회된 데이터 없음)이면 에러가 아니라 정상 빈 목록 처리
      if (response != null && "013".equals(response.status()) && page == 1) {
        break;
      }
      // 1-4 방어 로직: DART API 에러 또는 페이지네이션 불일치(누락/잘린 페이지) 차단
      requireSuccess(response == null ? null : response.status(), "regular reports");
      if (response.list() == null || response.list().isEmpty() || response.total_page() == null
          || response.total_page() < page || (response.page_no() != null && response.page_no() != page)) {
        throw new IllegalStateException("OpenDART returned an incomplete regular-report page");
      }
      for (Map<String, String> row : response.list()) {
        Optional<ReportingQuarter> quarter = ReportingQuarter.fromTitle(row.get("report_nm"), closingMonth);
        if (quarter.isPresent()) {
          String receipt = row.get("rcept_no");
          // 1-5 방어 로직: 접수번호(14자리) 누락 또는 다른 기업 공시가 섞여 들어온 경우 차단
          if (receipt == null || !receipt.matches("[0-9]{14}") || !corpCode.equals(row.get("corp_code"))) {
            throw new IllegalStateException("OpenDART returned invalid regular-report metadata");
          }
          // 1-6 방어 로직: 페이지 경계에서 같은 접수번호가 중복 내려와도 1건만 유지
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

  //2번 메서드
  private TargetQuarters selectTargetQuarters(TreeMap<ReportingQuarter, List<RegularReport>> available,
      Map<ReportingQuarter, List<StoredFiling>> previous) {
    ReportingQuarter earliest = available.lastKey().previous(7);
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

  private boolean isAlreadyStored(List<StoredFiling> stored, String receiptNo,
      boolean consolidated, boolean needsCumulative) {
    // 2-1 방어 로직: 동일 접수번호의 필수 실적(연결 여부·3분기 누적 포함)이 이미 DB에 있으면 API 재호출 스킵
    return stored.stream().anyMatch(filing ->
        filing.receiptNo().equals(receiptNo) && filing.hasAmounts(consolidated, needsCumulative));
  }

  //3번 메서드
  private FetchOutcome fetchMissingFinancials(String corpCode, int closingMonth, TargetQuarters targets,
      Map<ReportingQuarter, List<StoredFiling>> previous) {
    int metricsInserted = 0;
    Set<String> pending = new LinkedHashSet<>();

    for (var entry : targets.byQuarter().entrySet()) {
      ReportingQuarter quarter = entry.getKey();
      List<RegularReport> candidates = entry.getValue();
      Map<String, String> titles = candidates.stream()
          .collect(Collectors.toMap(RegularReport::receiptNo, RegularReport::title));

      // 3-1 방어 로직: 원본 없이 정정공시만 있거나 불완전한 공시 조합이면 pending으로 기록
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
      // 3-2 방어 로직: 이미 DB에 저장된 분기는 재무제표 API 호출 건너뛰기 (중복 호출·한도 절약)
      if (isAlreadyStored(previous.getOrDefault(quarter, List.of()), expected.receiptNo(),
          consolidated, needsCumulative)) {
        continue;
      }

      Optional<FinancialReport> result = fetchFinancialReport(corpCode, quarter, candidates, titles,
          consolidated, closingMonth != 12);
      if (result.isPresent()) {
        metricsInserted += saveFinancialReport(corpCode, result.get());
      }
      // 3-3 방어 로직: 공시는 떴는데 XBRL 재무 숫자가 아직 미반영된 경우 pending으로 기록
      if (result.filter(financial -> financial.receiptNo().equals(expected.receiptNo())
          && financial.hasAmounts(needsCumulative)).isEmpty()) {
        pending.add(quarter.end().toString());
      }
    }
    return new FetchOutcome(metricsInserted, pending);
  }

  private Optional<FinancialReport> fetchFinancialReport(String corpCode, ReportingQuarter quarter,
      List<RegularReport> reports, Map<String, String> titles, boolean hasConsolidated, boolean nonDecember) {
    var byReceipt = reports.stream()
        .filter(report -> ReportingQuarter.canUse(report.receiptNo(), titles))
        .collect(Collectors.toMap(RegularReport::receiptNo, Function.identity()));
    // 3-4 방어 로직: 연결('연') 표시가 없으면 연결(CFS) 먼저 시도 후 없으면 별도(OFS)로 폴백
    List<String> bases = hasConsolidated ? List.of("CFS") : List.of("CFS", "OFS");
    // 3-5 방어 로직: 비(非)12월 결산법인은 DART 사업연도 표기가 1년 차이날 수 있어 당해·전년도 순차 조회
    List<Integer> years = nonDecember && quarter.fiscalYear() > XBRL_START.getYear()
        ? List.of(quarter.fiscalYear(), quarter.fiscalYear() - 1) : List.of(quarter.fiscalYear());

    for (String basis : bases) {
      for (int year : years) {
        var response = client.financialStatements(corpCode, year, quarter.reportCode(), basis);
        // 3-6 방어 로직: 해당 기준/연도에 데이터가 없으면(013) 다음 후보(OFS 또는 전년도)로 폴백
        if (response != null && "013".equals(response.status())) {
          continue;
        }
        // 3-7 방어 로직: DART 오류 응답 또는 빈 재무제표 응답 차단
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

  //4번 메서드
  private int saveReports(String corpCode, int closingMonth, List<RegularReport> regularReports) {
    if (regularReports.isEmpty()) {
      return 0;
    }
    Integer saved = transactionOperations.execute(status -> {
      RegularReport sample = regularReports.getFirst();
      // 4-1 방어 로직: 비관적 락(findForUpdate)으로 동시 수집 시 중복 기업 생성 방지
      companyRepository.insertIfAbsent(UUID.randomUUID(), corpCode, sample.stockCode(), sample.companyName(),
          sample.corporationClass(), closingMonth, DisclosureCompany.isDefaultCapexCorp(corpCode));
      DisclosureCompany company = companyRepository.findForUpdate(corpCode).orElseThrow();
      company.updateProfile(sample.stockCode(), sample.companyName(), sample.corporationClass(), closingMonth);

      int inserted = 0;
      for (RegularReport report : regularReports) {
        // 4-2 방어 로직: 접수번호 중복 체크(Upsert) + 다른 기업 소속 접수번호 혼입 차단
        var existing = filingRepository.findByReceiptNo(report.receiptNo());
        DisclosureFiling filing = existing.orElseGet(() -> filingRepository.save(toEntity(company, report)));
        if (!company.getCorpCode().equals(filing.getCompany().getCorpCode())) {
          throw new IllegalStateException("Disclosure receipt belongs to another company");
        }
        if (filing.getBusinessYear() == null || filing.getReportCode() == null) {
          filing.setFinancialReport(report.quarter().fiscalYear(), report.quarter().reportCode());
        }
        if (existing.isEmpty()) {
          inserted++;
        }
      }
      return inserted;
    });
    return saved == null ? 0 : saved;
  }

  private int saveFinancialReport(String corpCode, FinancialReport financial) {
    Integer saved = transactionOperations.execute(status -> {
      // 4-3 방어 로직: 선행 저장된 기업·보고서가 없거나 기업 불일치 시 저장 중단
      companyRepository.findForUpdate(corpCode)
          .orElseThrow(() -> new IllegalStateException("Missing company for financial report"));
      DisclosureFiling filing = filingRepository.findByReceiptNo(financial.receiptNo())
          .orElseThrow(() -> new IllegalStateException("Missing regular report for financial receipt"));
      if (!corpCode.equals(filing.getCompany().getCorpCode())) {
        throw new IllegalStateException("Disclosure receipt belongs to another company");
      }
      filing.setFinancialReport(financial.businessYear(), financial.quarter().reportCode());
      String currentPeriodType = financial.quarter().number() == 4
          ? EarningsAccount.ANNUAL : EarningsAccount.CURRENT;
      int inserted = 0;
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
      return inserted;
    });
    return saved == null ? 0 : saved;
  }

  private int saveMetric(DisclosureFiling filing, EarningsRow row, String basis,
      String periodType, AmountKind kind, String metricType) {
    BigDecimal amount = row.amount(kind);
    // 4-4 방어 로직: 금액 누락 또는 이미 저장된 동일 지표 중복 insert 방지
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

  //엔티티 변환 및 DART 헬스 체크
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

  private void requireSuccess(String status, String operation) {
    if (!"000".equals(status)) {
      String safeStatus = status != null && status.matches("[0-9]{3}") ? status : "missing/invalid";
      throw new IllegalStateException("OpenDART " + operation + " failed, status=" + safeStatus);
    }
  }

  public record Counts(int filingsInserted, int metricsInserted) {
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
