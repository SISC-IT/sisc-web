package org.sejongisc.backend.disclosure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.disclosure.client.OpenDartClient;
import org.sejongisc.backend.disclosure.client.OpenDartClient.DartCompany;
import org.sejongisc.backend.disclosure.entity.DisclosureCardCategory;
import org.sejongisc.backend.disclosure.entity.DisclosureCardNews;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.sejongisc.backend.disclosure.repository.DisclosureCardNewsRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureCompanyRepository;
import org.sejongisc.backend.disclosure.service.DisclosureCardRuleEngine.CardDraft;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisclosureCardCollectionService {
  private static final DateTimeFormatter DART_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final String DART_VIEWER_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";

  private final OpenDartClient dartClient;
  private final DartDocumentTableParser documentParser;
  private final DisclosureCardRuleEngine ruleEngine;
  private final DisclosureEarningsQueryService earningsQueryService;
  private final DisclosureCompanyRepository companyRepository;
  private final DisclosureCardNewsRepository cardRepository;
  private final TransactionTemplate transactionTemplate;
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

    List<PendingCard> pending = new ArrayList<>();

    // 1. 기존 확정 실적 동기화 (추가 DART 호출 없음)
    earningsQueryService.latest(corpCode).ifPresent(earnings ->
        ruleEngine.evaluateConfirmedEarnings(earnings).ifPresent(draft -> {
          String receiptNo = earnings.quarters().getLast().sources().getFirst().receiptNo();
          pending.add(new PendingCard(
              corpCode, profile.stock_code(), profile.corp_name(), profile.corp_cls(),
              profile.induty_code(), closingMonth, receiptNo, draft.headline(), draft,
              Map.of("source", "confirmed_earnings")));
        }));

    // 2. 주주환원 — 자사주 취득 결정 (전용 JSON API)
    var treasuryRes = dartClient.treasuryStockAcquisitions(corpCode, from, to);
    if (treasuryRes != null && "000".equals(treasuryRes.status()) && treasuryRes.list() != null) {
      for (Map<String, String> row : treasuryRes.list()) {
        String receiptNo = row.get("rcept_no");
        if (receiptNo == null || !receiptNo.matches("[0-9]{14}")) {
          continue;
        }
        LocalDate rceptDate = parseReceiptDate(receiptNo);
        ruleEngine.evaluateTreasuryStockAcquisition(profile.corp_name(), row, rceptDate)
            .ifPresent(draft -> pending.add(new PendingCard(
                corpCode, profile.stock_code(), profile.corp_name(), profile.corp_cls(),
                profile.induty_code(), closingMonth, receiptNo,
                "자기주식 취득 결정", draft, row)));
      }
    }

    // 3. 거래소공시(I) — 잠정 실적, 배당/소각, 반도체 CAPEX
    collectExchangeDisclosures(corpCode, profile.induty_code(), from, to, pending);

    return persistCards(pending);
  }

  public int collectMarketWide(LocalDate from, LocalDate to) {
    List<PendingCard> pending = new ArrayList<>();
    collectExchangeDisclosures(null, null, from, to, pending);
    return persistCards(pending);
  }

  private void collectExchangeDisclosures(String corpCodeFilter, String knownIndutyCode,
      LocalDate from, LocalDate to, List<PendingCard> pending) {
    Map<String, DartCompany> companyCache = new HashMap<>();

    for (int page = 1; ; page++) {
      var res = dartClient.searchDisclosures(corpCodeFilter, "I", from, to, page, 100, true);
      if (res == null || "013".equals(res.status()) || res.list() == null || res.list().isEmpty()) {
        break;
      }
      if (!"000".equals(res.status())) {
        throw new IllegalStateException("OpenDART exchange disclosure list failed: status=" + res.status());
      }

      for (Map<String, String> row : res.list()) {
        String marketCls = row.get("corp_cls");
        if (!"Y".equals(marketCls) && !"K".equals(marketCls)) {
          continue;
        }
        String corpCode = row.get("corp_code");
        String receiptNo = row.get("rcept_no");
        String reportTitle = row.get("report_nm");
        if (corpCode == null || receiptNo == null || !receiptNo.matches("[0-9]{14}")) {
          continue;
        }

        String compactTitle = DartDocumentTableParser.compactKey(reportTitle);
        boolean isCapexCandidate = compactTitle.contains("신규시설투자");
        String indutyCode = knownIndutyCode;
        int closingMonth = 12;

        if (isCapexCandidate && indutyCode == null) {
          DartCompany info = companyCache.computeIfAbsent(corpCode, dartClient::company);
          if (info != null && "000".equals(info.status())) {
            indutyCode = info.induty_code();
            if (info.acc_mt() != null && info.acc_mt().matches("0[1-9]|1[0-2]")) {
              closingMonth = Integer.parseInt(info.acc_mt());
            }
          }
        }

        Optional<DisclosureCardCategory> categoryOpt =
            ruleEngine.classifyExchangeTitle(reportTitle, indutyCode);
        if (categoryOpt.isEmpty()) {
          continue;
        }

        LocalDate publishedDate = parseDartDate(row.get("rcept_dt"), receiptNo);
        String xml = dartClient.downloadDocument(receiptNo);
        var parsedDoc = documentParser.parse(xml);

        Optional<CardDraft> draftOpt = switch (categoryOpt.get()) {
          case PRELIMINARY_EARNINGS -> ruleEngine.evaluatePreliminaryEarnings(
              row.get("corp_name"), reportTitle, publishedDate, parsedDoc);
          case SHAREHOLDER_RETURN -> ruleEngine.evaluateShareholderDocument(
              row.get("corp_name"), reportTitle, publishedDate, parsedDoc);
          case SEMICONDUCTOR_CAPEX -> ruleEngine.evaluateSemiconductorCapex(
              row.get("corp_name"), indutyCode, reportTitle, publishedDate, parsedDoc);
          case CONFIRMED_EARNINGS -> Optional.empty();
        };

        if (draftOpt.isPresent()) {
          pending.add(new PendingCard(
              corpCode, blankToNull(row.get("stock_code")), row.get("corp_name"), marketCls,
              indutyCode, closingMonth, receiptNo, reportTitle, draftOpt.get(), row));
        }
      }

      if (res.total_page() == null || page >= res.total_page()) {
        break;
      }
    }
  }

  private int persistCards(List<PendingCard> pending) {
    if (pending.isEmpty()) {
      return 0;
    }
    Integer saved = transactionTemplate.execute(status -> {
      int count = 0;
      for (PendingCard item : pending) {
        DisclosureCompany company = companyRepository.findForUpdate(item.corpCode())
            .orElseGet(() -> companyRepository.saveAndFlush(new DisclosureCompany(
                item.corpCode(), item.stockCode(), item.companyName(),
                item.marketCls(), item.closingMonth())));
        company.updateProfile(item.stockCode(), item.companyName(),
            item.marketCls(), item.closingMonth());
        company.updateIndutyCode(item.indutyCode());

        String metricsJson = toJson(item.draft().metrics());
        String tagsJson = toJson(item.draft().tags());
        String rawJson = toJson(item.rawPayload());
        LocalDateTime now = LocalDateTime.now();

        var existing = cardRepository.findByReceiptNoAndCategory(
            item.receiptNo(), item.draft().category());
        if (existing.isPresent()) {
          existing.get().refresh(item.reportTitle(), item.draft().headline(),
              item.draft().publishedDate(), item.marketCls(), metricsJson, tagsJson, rawJson, now);
        } else {
          cardRepository.save(new DisclosureCardNews(
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
      throw new IllegalStateException("Failed to serialize disclosure card payload", e);
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

  private record PendingCard(
      String corpCode,
      String stockCode,
      String companyName,
      String marketCls,
      String indutyCode,
      int closingMonth,
      String receiptNo,
      String reportTitle,
      CardDraft draft,
      Map<String, String> rawPayload) {
  }
}
