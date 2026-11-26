package org.sejongisc.backend.disclosure.service.settings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sejongisc.backend.disclosure.dto.DisclosureEventResponse.Metric;
import org.sejongisc.backend.disclosure.entity.DisclosureEventCategory;
import org.sejongisc.backend.disclosure.service.settings.DartDocumentTableParser.ParsedDocument;
import org.springframework.stereotype.Component;

// 4대 이벤트 공시 규칙 기반 필터링 엔진
@Component
public class DisclosureEventRuleEngine {
  private static final BigDecimal HUNDRED_MILLION = new BigDecimal("100000000");
  private static final BigDecimal TRILLION = new BigDecimal("1000000000000");

  private static final BigDecimal MIN_PRELIM_OP_YOY_PERCENT = new BigDecimal("20");
  private static final BigDecimal MIN_TREASURY_WON = new BigDecimal("10000000000"); // 100억
  private static final BigDecimal MIN_DIVIDEND_YIELD_PERCENT = new BigDecimal("3.5");
  private static final BigDecimal MIN_DIVIDEND_TOTAL_WON = new BigDecimal("50000000000"); // 500억
  private static final BigDecimal MIN_CAPEX_EQUITY_RATIO = new BigDecimal("5.0");
  private static final BigDecimal MIN_CAPEX_WON = new BigDecimal("30000000000"); // 300억

  // 수주 다빈도 업종(조선 311, 건설 41/42, 특수장비 292): 기준 상향
  private static final Set<String> HIGH_FREQ_ORDER_PREFIXES = Set.of("311", "41", "42", "292");
  private static final BigDecimal ORDER_RATIO_HIGH_FREQ = new BigDecimal("15.0");
  private static final BigDecimal ORDER_WON_HIGH_FREQ = new BigDecimal("100000000000"); // 1,000억
  private static final BigDecimal ORDER_RATIO_DEFAULT = new BigDecimal("10.0");
  private static final BigDecimal ORDER_WON_DEFAULT = new BigDecimal("10000000000"); // 100억

  public boolean isHighFrequencyOrderIndustry(String indutyCode) {
    if (indutyCode == null || indutyCode.isBlank()) {
      return false;
    }
    String normalized = indutyCode.trim();
    return HIGH_FREQ_ORDER_PREFIXES.stream().anyMatch(normalized::startsWith);
  }

  public boolean isCorrectionOrAutonomousNoise(String reportTitle) {
    if (reportTitle == null) {
      return true;
    }
    String title = reportTitle.strip();
    return title.startsWith("[기재정정]") || title.contains("(자율공시)");
  }

  public Optional<DisclosureEventCategory> classifyExchangeTitle(String reportTitle,
      boolean trackCapex) {
    if (reportTitle == null || reportTitle.isBlank() || isCorrectionOrAutonomousNoise(reportTitle)) {
      return Optional.empty();
    }
    String compact = DartDocumentTableParser.compactKey(reportTitle);
    if (compact.contains("단일판매공급계약체결") || compact.contains("단일판매공급계약해지")) {
      return Optional.of(DisclosureEventCategory.ORDER_CONTRACT);
    }
    if (compact.contains("영업잠정실적") || compact.contains("매출액또는손익구조")) {
      return Optional.of(DisclosureEventCategory.PRELIMINARY_EARNINGS);
    }
    if (compact.contains("현금현물배당결정") || compact.contains("주식소각결정")) {
      return Optional.of(DisclosureEventCategory.SHAREHOLDER_RETURN);
    }
    if (compact.contains("신규시설투자") && trackCapex) {
      return Optional.of(DisclosureEventCategory.CAPEX);
    }
    return Optional.empty();
  }

  public Optional<EventDraft> evaluateOrderContract(String companyName, String indutyCode,
      String reportTitle, LocalDate publishedDate, ParsedDocument doc) {
    boolean cancellation = reportTitle != null && reportTitle.contains("해지");
    boolean confidential = doc.findValue("유보사유", "유보기한")
        .map(DartDocumentTableParser::compactKey)
        .filter(value -> !List.of("", "-", "–", "—", "해당없음", "해당사항없음", "없음").contains(value))
        .isPresent();

    BigDecimal contractAmount = doc.findValue("확정계약금액", "계약금액", "계약금액총액")
        .flatMap(DartDocumentTableParser::parseNumber).orElse(null);
    BigDecimal salesRatio = doc.findValue("매출액대비", "최근매출액대비")
        .flatMap(DartDocumentTableParser::parseNumber).orElse(null);
    String counterparty = doc.findValue("계약상대방", "계약상대")
        .orElse(confidential ? "경영상 비밀유지(유보)" : "-");
    String detail = doc.findValue("계약내용", "판매공급계약내용").orElse(reportTitle);

    boolean highFreq = isHighFrequencyOrderIndustry(indutyCode);
    BigDecimal minRatio = highFreq ? ORDER_RATIO_HIGH_FREQ : ORDER_RATIO_DEFAULT;
    BigDecimal minWon = highFreq ? ORDER_WON_HIGH_FREQ : ORDER_WON_DEFAULT;

    boolean significant = cancellation
        || confidential
        || (salesRatio != null && salesRatio.compareTo(minRatio) >= 0)
        || (contractAmount != null && contractAmount.compareTo(minWon) >= 0);
    if (!significant) {
      return Optional.empty();
    }

    String amountText = contractAmount != null ? formatWon(contractAmount) : (confidential ? "유보" : "-");
    String ratioText = salesRatio != null ? salesRatio.setScale(1, RoundingMode.HALF_UP) + "%" : "-";
    String headline = cancellation
        ? String.format("공급계약 해지 — %s (매출대비 %s)", truncate(detail, 26), ratioText)
        : String.format("%s — 계약금액 %s (매출대비 %s)", truncate(detail, 26), amountText, ratioText);

    List<Metric> metrics = List.of(
        new Metric("계약금액", amountText, false),
        new Metric("매출대비", ratioText, true),
        new Metric("계약상대방", truncate(counterparty, 18), false)
    );
    List<String> tags = new ArrayList<>();
    tags.add(cancellation ? "계약해지" : "수주공시");
    if (confidential) {
      tags.add("유보");
    }
    if (highFreq) {
      tags.add("수주업종기준");
    }
    return Optional.of(new EventDraft(DisclosureEventCategory.ORDER_CONTRACT, headline,
        publishedDate, metrics, tags));
  }

  public Optional<EventDraft> evaluatePreliminaryEarnings(String companyName, String reportTitle,
      LocalDate publishedDate, ParsedDocument doc) {
    String unit = detectAmountUnit(doc.plainText());
    String revenue = extractRowLatestValue(doc, "매출액", "영업수익").orElse("-");
    String opProfit = extractRowLatestValue(doc, "영업이익").orElse("-");
    String opYoyRaw = extractRowYoyPercent(doc, "영업이익").orElse(null);
    String netIncome = extractRowLatestValue(doc, "당기순이익", "분기순이익", "반기순이익").orElse("-");

    String comparison = opYoyRaw == null ? "" : DartDocumentTableParser.compactKey(opYoyRaw);
    boolean turnaround = "흑자전환".equals(comparison) || "적자전환".equals(comparison);
    BigDecimal opYoy = opYoyRaw != null ? DartDocumentTableParser.parseNumber(opYoyRaw).orElse(null) : null;

    boolean significant = turnaround
        || (opYoy != null && opYoy.abs().compareTo(MIN_PRELIM_OP_YOY_PERCENT) >= 0)
        || (opYoy == null && !"-".equals(opProfit));
    if (!significant) {
      return Optional.empty();
    }

    String opDisplay = formatWithUnit(opProfit, unit);
    if (opYoy != null) {
      opDisplay = opDisplay + " (" + formatSignedPercent(opYoy) + ")";
    } else if ("흑자전환".equals(comparison)) {
      opDisplay = opDisplay + " (흑자전환)";
    } else if ("적자전환".equals(comparison)) {
      opDisplay = opDisplay + " (적자전환)";
    }

    String headline = buildPreliminaryHeadline(reportTitle, opYoy, comparison);
    List<Metric> metrics = List.of(
        new Metric("매출액", formatWithUnit(revenue, unit), false),
        new Metric("영업이익", opDisplay, true),
        new Metric("당기순이익", formatWithUnit(netIncome, unit), false)
    );
    List<String> tags = new ArrayList<>();
    tags.add("잠정실적");
    if ("흑자전환".equals(comparison)) {
      tags.add("흑자전환");
    } else if ("적자전환".equals(comparison)) {
      tags.add("적자전환");
    } else if (opYoy != null && opYoy.compareTo(MIN_PRELIM_OP_YOY_PERCENT) >= 0) {
      tags.add("영업이익 20%↑");
    } else if (opYoy != null && opYoy.compareTo(MIN_PRELIM_OP_YOY_PERCENT.negate()) <= 0) {
      tags.add("영업이익 20%↓");
    }
    return Optional.of(new EventDraft(DisclosureEventCategory.PRELIMINARY_EARNINGS, headline,
        publishedDate, metrics, tags));
  }

  public Optional<EventDraft> evaluateTreasuryStockAcquisition(String companyName,
      Map<String, String> dartRow, LocalDate publishedDate) {
    BigDecimal plannedAmount = DartDocumentTableParser.parseNumber(dartRow.get("aqpln_prc_ostk"))
        .or(() -> DartDocumentTableParser.parseNumber(dartRow.get("aqpln_prc_estk")))
        .or(() -> DartDocumentTableParser.parseNumber(dartRow.get("aqplnprc_ostk")))
        .or(() -> DartDocumentTableParser.parseNumber(dartRow.get("aqplnprc_estk")))
        .orElse(null);
    BigDecimal plannedShares = DartDocumentTableParser.parseNumber(dartRow.get("aqpln_stk_ostk"))
        .or(() -> DartDocumentTableParser.parseNumber(dartRow.get("aqpln_stk_estk")))
        .orElse(null);
    String purpose = dartRow.getOrDefault("aq_pp", "");
    boolean cancellation = purpose.contains("소각");

    if (!cancellation && (plannedAmount == null || plannedAmount.compareTo(MIN_TREASURY_WON) < 0)) {
      return Optional.empty();
    }

    String amountText = plannedAmount != null ? formatWon(plannedAmount) : "-";
    String sharesText = plannedShares != null ? String.format("%,d주", plannedShares.longValue()) : "-";
    String headlineMetric = plannedAmount != null ? amountText : sharesText;
    String purposeShort = cancellation ? "주주가치 제고 (소각)" : (purpose.isBlank() ? "주주가치 제고" : truncate(purpose, 18));
    String headline = cancellation
        ? String.format("자기주식 %s 취득 및 소각 결정", headlineMetric)
        : String.format("자기주식 %s 취득 결정", headlineMetric);

    List<Metric> metrics = List.of(
        new Metric("취득예정금액", amountText, true),
        new Metric("취득예정주식", sharesText, false),
        new Metric("취득목적", purposeShort, false)
    );
    List<String> tags = cancellation
        ? List.of("주주환원", "자사주 소각")
        : List.of("주주환원", "자사주 취득");
    return Optional.of(new EventDraft(DisclosureEventCategory.SHAREHOLDER_RETURN, headline,
        publishedDate, metrics, tags));
  }

  public Optional<EventDraft> evaluateShareholderDocument(String companyName, String reportTitle,
      LocalDate publishedDate, ParsedDocument doc) {
    String compactTitle = DartDocumentTableParser.compactKey(reportTitle);
    if (compactTitle.contains("주식소각결정")) {
      BigDecimal amount = doc.findValue("소각예정금액", "소각금액")
          .flatMap(DartDocumentTableParser::parseNumber).orElse(null);
      String shares = doc.findValue("소각할주식의수", "소각주식수", "보통주식").orElse("-");
      String amountText = amount != null ? formatWon(amount) : "-";
      String headline = amount != null
          ? String.format("자기주식 %s 소각 결정", amountText)
          : "자기주식 소각 결정";
      List<Metric> metrics = List.of(
          new Metric("소각예정금액", amountText, true),
          new Metric("소각주식수", truncate(shares, 16), false),
          new Metric("구분", "주식소각", false)
      );
      return Optional.of(new EventDraft(DisclosureEventCategory.SHAREHOLDER_RETURN, headline,
          publishedDate, metrics, List.of("주주환원", "자사주 소각")));
    }

    BigDecimal dps = doc.findValue("1주당배당금", "보통주식배당금")
        .flatMap(DartDocumentTableParser::parseNumber).orElse(null);
    BigDecimal yield = doc.findValue("시가배당율", "시가배당률")
        .flatMap(DartDocumentTableParser::parseNumber).orElse(null);
    BigDecimal totalDiv = doc.findValue("배당금총액")
        .flatMap(DartDocumentTableParser::parseNumber).orElse(null);

    boolean significant = (yield != null && yield.compareTo(MIN_DIVIDEND_YIELD_PERCENT) >= 0)
        || (totalDiv != null && totalDiv.compareTo(MIN_DIVIDEND_TOTAL_WON) >= 0);
    if (!significant) {
      return Optional.empty();
    }

    String dpsText = dps != null ? String.format("%,d원", dps.longValue()) : "-";
    String yieldText = yield != null ? yield.setScale(1, RoundingMode.HALF_UP) + "%" : "-";
    String totalText = totalDiv != null ? formatWon(totalDiv) : "-";
    String headline = String.format("현금배당 결정 — 주당 %s (시가배당률 %s)", dpsText, yieldText);

    List<Metric> metrics = List.of(
        new Metric("주당배당금", dpsText, false),
        new Metric("시가배당률", yieldText, true),
        new Metric("배당금총액", totalText, false)
    );
    return Optional.of(new EventDraft(DisclosureEventCategory.SHAREHOLDER_RETURN, headline,
        publishedDate, metrics, List.of("주주환원", "현금배당")));
  }

  public Optional<EventDraft> evaluateCapex(String companyName, boolean trackCapex,
      String reportTitle, LocalDate publishedDate, ParsedDocument doc) {
    if (!trackCapex) {
      return Optional.empty();
    }
    BigDecimal investAmount = doc.findValue("투자금액", "투자내역투자금액")
        .flatMap(DartDocumentTableParser::parseNumber).orElse(null);
    BigDecimal equityRatio = doc.findValue("자기자본대비")
        .flatMap(DartDocumentTableParser::parseNumber).orElse(null);
    String purpose = doc.findValue("투자목적", "투자대상").orElse("신규 시설 투자");
    String startDate = doc.findValue("시작일", "투자기간시작일").orElse("");
    String endDate = doc.findValue("종료일", "투자기간종료일").orElse("");

    boolean significant = (equityRatio != null && equityRatio.compareTo(MIN_CAPEX_EQUITY_RATIO) >= 0)
        || (investAmount != null && investAmount.compareTo(MIN_CAPEX_WON) >= 0);
    if (!significant) {
      return Optional.empty();
    }

    String amountText = investAmount != null ? formatWon(investAmount) : "-";
    String ratioText = equityRatio != null ? equityRatio.setScale(1, RoundingMode.HALF_UP) + "%" : "-";
    String periodText = (!startDate.isBlank() && !endDate.isBlank())
        ? extractYear(startDate) + "~" + extractYear(endDate) : "-";
    String headline = String.format("신규시설투자 %s 결정 — %s", amountText, truncate(purpose, 24));

    List<Metric> metrics = List.of(
        new Metric("투자금액", amountText, false),
        new Metric("자기자본대비", ratioText, true),
        new Metric("투자기간", periodText, false)
    );
    return Optional.of(new EventDraft(DisclosureEventCategory.CAPEX, headline,
        publishedDate, metrics, List.of("CAPEX", "시설투자")));
  }

  private String buildPreliminaryHeadline(String reportTitle, BigDecimal opYoy, String comparison) {
    if ("흑자전환".equals(comparison)) {
      return "잠정 실적 발표 — 영업이익 흑자전환";
    }
    if ("적자전환".equals(comparison)) {
      return "잠정 실적 발표 — 영업이익 적자전환";
    }
    if (opYoy != null) {
      String dir = opYoy.signum() >= 0 ? "증가" : "감소";
      return String.format("잠정 실적 발표 — 영업이익 전년비 %s %s", formatSignedPercent(opYoy), dir);
    }
    return truncate(reportTitle, 42);
  }

  private Optional<String> extractRowLatestValue(ParsedDocument doc, String... labels) {
    return doc.findRowContaining(labels)
        .flatMap(row -> row.stream().skip(1)
            .filter(cell -> DartDocumentTableParser.parseNumber(cell).isPresent())
            .findFirst());
  }

  private Optional<String> extractRowYoyPercent(ParsedDocument doc, String... labels) {
    return doc.findRowContaining(labels)
        .flatMap(row -> {
          for (List<String> header : doc.rows()) {
            if (header == row) {
              break;
            }
            if (header.size() != row.size()) {
              continue;
            }
            for (int i = 1; i < header.size(); i++) {
              String label = DartDocumentTableParser.compactKey(header.get(i));
              if ((label.contains("전년동기") || label.contains("전년동기대비"))
                  && (label.contains("대비") || label.contains("%") || label.contains("증감률"))) {
                return Optional.of(row.get(i));
              }
            }
          }
          return Optional.empty();
        });
  }

  private String detectAmountUnit(String plainText) {
    if (plainText.contains("단위 : 백만원") || plainText.contains("단위:백만원") || plainText.contains("(백만원)")) {
      return "백만원";
    }
    if (plainText.contains("단위 : 억원") || plainText.contains("단위:억원") || plainText.contains("(억원)")) {
      return "억원";
    }
    return "원";
  }

  private String formatWithUnit(String rawNumber, String unit) {
    if (rawNumber == null || "-".equals(rawNumber)) {
      return "-";
    }
    Optional<BigDecimal> parsed = DartDocumentTableParser.parseNumber(rawNumber);
    if (parsed.isEmpty()) {
      return rawNumber;
    }
    BigDecimal value = parsed.get();
    if ("백만원".equals(unit)) {
      return formatWon(value.multiply(new BigDecimal("1000000")));
    }
    if ("억원".equals(unit)) {
      return formatWon(value.multiply(HUNDRED_MILLION));
    }
    return formatWon(value);
  }

  public static String formatWon(BigDecimal won) {
    if (won == null) {
      return "-";
    }
    BigDecimal abs = won.abs();
    String sign = won.signum() < 0 ? "-" : "";
    if (abs.compareTo(TRILLION) >= 0) {
      BigDecimal jo = abs.divide(TRILLION, 2, RoundingMode.HALF_UP);
      return sign + jo.stripTrailingZeros().toPlainString() + "조";
    }
    if (abs.compareTo(HUNDRED_MILLION) >= 0) {
      long eok = abs.divide(HUNDRED_MILLION, 0, RoundingMode.HALF_UP).longValue();
      return String.format("%s%,d억", sign, eok);
    }
    return String.format("%s%,d원", sign, abs.longValue());
  }

  private static String formatSignedPercent(BigDecimal pct) {
    BigDecimal rounded = pct.setScale(1, RoundingMode.HALF_UP);
    return (rounded.signum() > 0 ? "+" : "") + rounded.toPlainString() + "%";
  }

  private static String extractYear(String dateText) {
    return dateText.length() >= 4 ? dateText.substring(0, 4) : dateText;
  }

  private static String truncate(String value, int maxLen) {
    if (value == null) {
      return "";
    }
    String clean = value.trim();
    return clean.length() <= maxLen ? clean : clean.substring(0, maxLen - 1) + "…";
  }

  public record EventDraft(
      DisclosureEventCategory category,
      String headline,
      LocalDate publishedDate,
      List<Metric> metrics,
      List<String> tags) {
  }
}
