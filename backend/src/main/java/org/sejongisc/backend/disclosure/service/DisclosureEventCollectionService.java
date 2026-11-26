package org.sejongisc.backend.disclosure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.disclosure.client.OpenDartClient;
import org.sejongisc.backend.disclosure.client.OpenDartClient.DartCompany;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.sejongisc.backend.disclosure.entity.DisclosureEvent;
import org.sejongisc.backend.disclosure.entity.DisclosureEventCategory;
import org.sejongisc.backend.disclosure.repository.DisclosureCompanyRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureEventRepository;
import org.sejongisc.backend.disclosure.service.settings.DartDocumentTableParser;
import org.sejongisc.backend.disclosure.service.settings.DisclosureEventRuleEngine;
import org.sejongisc.backend.disclosure.service.settings.DisclosureEventRuleEngine.EventDraft;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisclosureEventCollectionService {
  private static final DateTimeFormatter DART_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final String DART_VIEWER_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";

  private final OpenDartClient dartClient;
  private final DartDocumentTableParser documentParser;
  private final DisclosureEventRuleEngine ruleEngine;
  private final DisclosureCompanyRepository companyRepository;
  private final DisclosureEventRepository eventRepository;
  private final TransactionOperations transactionOperations;
  private final ObjectMapper objectMapper;

  public int collectForCompany(String corpCode, LocalDate from, LocalDate to) {
    if (corpCode == null || !corpCode.matches("[0-9]{8}")) {
      throw new IllegalArgumentException("Provide an 8-digit DART corp_code");
    }

    DartCompany profile = dartClient.company(corpCode);
    if (profile == null || !"000".equals(profile.status())) {
      throw new IllegalStateException("OpenDART company lookup failed for corpCode=" + corpCode);
    }
    int closingMonth = (profile.acc_mt() != null && profile.acc_mt().matches("0[1-9]|1[0-2]"))
        ? Integer.parseInt(profile.acc_mt()) : 12;

    boolean trackCapex = companyRepository.findByCorpCode(corpCode)
        .map(DisclosureCompany::isTrackCapex)
        .orElseGet(() -> DisclosureCompany.isDefaultCapexCorp(corpCode));

    List<PendingEvent> pending = new ArrayList<>();

    // 1. 주주환원 — 자사주 취득 결정 (전용 JSON API)
    var treasuryRes = dartClient.treasuryStockAcquisitions(corpCode, from, to);
    if ("000".equals(treasuryRes.status())) {
      for (Map<String, String> row : treasuryRes.list()) {
        String receiptNo = row.get("rcept_no");
        if (receiptNo == null || !receiptNo.matches("[0-9]{14}")) {
          continue;
        }
        LocalDate rceptDate = parseReceiptDate(receiptNo);
        ruleEngine.evaluateTreasuryStockAcquisition(profile.corp_name(), row, rceptDate)
            .ifPresent(draft -> pending.add(new PendingEvent(
                corpCode, profile.stock_code(), profile.corp_name(), profile.corp_cls(),
                profile.induty_code(), closingMonth, receiptNo,
                "자기주식 취득 결정", draft, row)));
      }
    }

    // 2. 거래소공시(I) — 수주공시, 잠정실적, 배당/소각, CAPEX(지정 기업)
    collectExchangeDisclosures(corpCode, profile.induty_code(), closingMonth, trackCapex, from, to, pending);

    return persistEvents(pending);
  }

  private void collectExchangeDisclosures(String corpCode, String indutyCode, int closingMonth,
      boolean trackCapex, LocalDate from, LocalDate to, List<PendingEvent> pending) {
    for (int page = 1; ; page++) {
      var res = dartClient.searchDisclosures(corpCode, "I", from, to, page, 100, false);
      String status = res == null ? null : res.status();
      if (page == 1 && "013".equals(status)) {
        break;
      }
      if (!"000".equals(status)) {
        throw new IllegalStateException("OpenDART exchange disclosure page failed: status=" + status);
      }
      if (res.list() == null || res.list().isEmpty()) {
        break;
      }
      if (res.total_page() == null || res.total_page() < page
          || (res.page_no() != null && res.page_no() != page)) {
        throw new IllegalStateException("OpenDART exchange disclosure page failed: status=" + status);
      }

      for (Map<String, String> row : res.list()) {
        String marketCls = row.get("corp_cls");
        if (!"Y".equals(marketCls) && !"K".equals(marketCls)) {
          continue;
        }
        String receiptNo = row.get("rcept_no");
        String reportTitle = row.get("report_nm");
        if (receiptNo == null || !receiptNo.matches("[0-9]{14}")) {
          continue;
        }

        Optional<DisclosureEventCategory> categoryOpt =
            ruleEngine.classifyExchangeTitle(reportTitle, trackCapex);
        if (categoryOpt.isEmpty()) {
          continue;
        }

        // 이미 DB에 저장된 공시면 원문(/document.xml) 재다운로드 및 파싱 생략
        if (eventRepository.findByReceiptNoAndCategory(receiptNo, categoryOpt.get()).isPresent()) {
          continue;
        }

        LocalDate publishedDate = parseDartDate(row.get("rcept_dt"), receiptNo);
        String xml = dartClient.downloadDocument(receiptNo);
        var parsedDoc = documentParser.parse(xml);

        Optional<EventDraft> draftOpt = switch (categoryOpt.get()) {
          case ORDER_CONTRACT -> ruleEngine.evaluateOrderContract(
              row.get("corp_name"), indutyCode, reportTitle, publishedDate, parsedDoc);
          case PRELIMINARY_EARNINGS -> ruleEngine.evaluatePreliminaryEarnings(
              row.get("corp_name"), reportTitle, publishedDate, parsedDoc);
          case SHAREHOLDER_RETURN -> ruleEngine.evaluateShareholderDocument(
              row.get("corp_name"), reportTitle, publishedDate, parsedDoc);
          case CAPEX -> ruleEngine.evaluateCapex(
              row.get("corp_name"), trackCapex, reportTitle, publishedDate, parsedDoc);
        };

        if (draftOpt.isPresent()) {
          pending.add(new PendingEvent(
              corpCode, blankToNull(row.get("stock_code")), row.get("corp_name"), marketCls,
              indutyCode, closingMonth, receiptNo, reportTitle, draftOpt.get(), row));
        }
      }

      if (page == res.total_page()) {
        break;
      }
    }
  }

  private int persistEvents(List<PendingEvent> pending) {
    if (pending.isEmpty()) {
      return 0;
    }
    Integer saved = transactionOperations.execute(status -> {
      int count = 0;
      for (PendingEvent item : pending) {
        companyRepository.insertIfAbsent(UUID.randomUUID(), item.corpCode(), item.stockCode(), item.companyName(),
            item.marketCls(), item.closingMonth(), DisclosureCompany.isDefaultCapexCorp(item.corpCode()));
        DisclosureCompany company = companyRepository.findForUpdate(item.corpCode()).orElseThrow();
        company.updateProfile(item.stockCode(), item.companyName(),
            item.marketCls(), item.closingMonth());
        company.updateIndutyCode(item.indutyCode());

        String metricsJson = toJson(item.draft().metrics());
        String tagsJson = toJson(item.draft().tags());
        String rawJson = toJson(item.rawPayload());
        LocalDateTime now = LocalDateTime.now();

        var existing = eventRepository.findByReceiptNoAndCategory(
            item.receiptNo(), item.draft().category());
        if (existing.isPresent()) {
          existing.get().refresh(item.reportTitle(), item.draft().headline(),
              item.draft().publishedDate(), item.marketCls(), metricsJson, tagsJson, rawJson, now);
        } else {
          eventRepository.save(new DisclosureEvent(
              company, item.receiptNo(), item.draft().category(), item.reportTitle(),
              item.draft().headline(), item.draft().publishedDate(), item.marketCls(),
              metricsJson, tagsJson, DART_VIEWER_URL + item.receiptNo(), rawJson, now));
          count++;
        }
      }
      return count;
    });
    return saved == null ? 0 : saved;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize disclosure event payload", e);
    }
  }

  private static LocalDate parseDartDate(String yyyymmdd, String receiptNo) {
    if (yyyymmdd != null && yyyymmdd.matches("[0-9]{8}")) {
      return LocalDate.parse(yyyymmdd, DART_DATE);
    }
    return parseReceiptDate(receiptNo);
  }

  private static LocalDate parseReceiptDate(String receiptNo) {
    return LocalDate.parse(receiptNo.substring(0, 8), DART_DATE);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private record PendingEvent(
      String corpCode,
      String stockCode,
      String companyName,
      String marketCls,
      String indutyCode,
      int closingMonth,
      String receiptNo,
      String reportTitle,
      EventDraft draft,
      Map<String, String> rawPayload) {
  }
}
