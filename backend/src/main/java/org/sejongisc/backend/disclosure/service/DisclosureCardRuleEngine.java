package org.sejongisc.backend.disclosure.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse.Metric;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse;
import org.sejongisc.backend.disclosure.entity.DisclosureCardCategory;
import org.sejongisc.backend.disclosure.service.DartDocumentTableParser.ParsedDocument;
import org.springframework.stereotype.Component;

@Component
public class DisclosureCardRuleEngine {
  private static final BigDecimal HUNDRED_MILLION = new BigDecimal("100000000");
  private static final BigDecimal TRILLION = new BigDecimal("1000000000000");

  private static final BigDecimal MIN_PRELIM_OP_YOY_PERCENT = new BigDecimal("20");
  private static final BigDecimal MIN_TREASURY_WON = new BigDecimal("10000000000"); // 100억
  private static final BigDecimal MIN_DIVIDEND_YIELD_PERCENT = new BigDecimal("3.5");
  private static final BigDecimal MIN_DIVIDEND_TOTAL_WON = new BigDecimal("50000000000"); // 500억
  private static final BigDecimal MIN_CAPEX_EQUITY_RATIO = new BigDecimal("5.0");
  private static final BigDecimal MIN_CAPEX_WON = new BigDecimal("30000000000"); // 300억

  // KSIC: 261xx (반도체 제조업), 29271 (반도체 제조용 기계 제조업)
  private static final Set<String> SEMICONDUCTOR_PREFIXES = Set.of("261", "29271");

  public boolean isSemiconductorIndustry(String indutyCode) {
    if (indutyCode == null || indutyCode.isBlank()) {
      return false;
    }
    String normalized = indutyCode.trim();
    return SEMICONDUCTOR_PREFIXES.stream().anyMatch(normalized::startsWith);
  }

  public boolean isCorrectionOrAutonomousNoise(String reportTitle) {
    if (reportTitle == null) {
      return true;
    }
    String title = reportTitle.strip();
    return title.startsWith("[기재정정]") || title.contains("(자율공시)");
  }

  public Optional<DisclosureCardCategory> classifyExchangeTitle(String reportTitle,
      String indutyCode) {
    if (reportTitle == null || reportTitle.isBlank() || isCorrectionOrAutonomousNoise(reportTitle)) {
      return Optional.empty();
    }
    String compact = DartDocumentTableParser.compactKey(reportTitle);
    if (compact.contains("영업잠정실적") || compact.contains("매출액또는손익구조")) {
      return Optional.of(DisclosureCardCategory.PRELIMINARY_EARNINGS);
    }
    if (compact.contains("현금현물배당결정") || compact.contains("주식소각결정")) {
      return Optional.of(DisclosureCardCategory.SHAREHOLDER_RETURN);
    }
    if (compact.contains("신규시설투자") && isSemiconductorIndustry(indutyCode)) {
      return Optional.of(DisclosureCardCategory.SEMICONDUCTOR_CAPEX);
    }
    return Optional.empty();
  }

  public Optional<CardDraft> evaluatePreliminaryEarnings(String companyName, String reportTitle,
      LocalDate publishedDate, ParsedDocument doc) {
    String unit = detectAmountUnit(doc.plainText());
    String revenue = extractRowLatestValue(doc, "매출액", "영업수익").orElse("-");
    String opProfit = extractRowLatestValue(doc, "영업이익").orElse("-");
    String opYoyRaw = extractRowYoyPercent(doc, "영업이익").orElse(null);
    String netIncome = extractRowLatestValue(doc, "당기순이익", "법인세비용차감전").orElse("-");

    boolean turnaround = doc.plainText().contains("흑자전환") || doc.plainText().contains("적자전환");
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
    } else if (doc.plainText().contains("흑자전환")) {
      opDisplay = opDisplay + " (흑자전환)";
    } else if (doc.plainText().contains("적자전환")) {
      opDisplay = opDisplay + " (적자전환)";
    }

    String headline = buildPreliminaryHeadline(companyName, reportTitle, opYoy, doc.plainText());
    List<Metric> metrics = List.of(
        new Metric("매출액", formatWithUnit(revenue, unit), false),
        new Metric("영업이익", opDisplay, true),
        new Metric("당기순이익", formatWithUnit(netIncome, unit), false)
    );
    List<String> tags = new ArrayList<>();
    tags.add("잠정실적");
    if (doc.plainText().contains("흑자전환")) {
      tags.add("흑자전환");
    } else if (doc.plainText().contains("적자전환")) {
      tags.add("적자전환");
    } else if (opYoy != null && opYoy.compareTo(MIN_PRELIM_OP_YOY_PERCENT) >= 0) {
      tags.add("영업이익 20%↑");
    } else if (opYoy != null && opYoy.compareTo(MIN_PRELIM_OP_YOY_PERCENT.negate()) <= 0) {
      tags.add("영업이익 20%↓");
    }
    return Optional.of(new CardDraft(DisclosureCardCategory.PRELIMINARY_EARNINGS, headline,
        publishedDate, metrics, tags));
  }

  public Optional<CardDraft> evaluateConfirmedEarnings(DisclosureEarningsResponse earnings) {
    if (earnings == null || earnings.quarters() == null || earnings.quarters().isEmpty()) {
      return Optional.empty();
    }
    DisclosureEarningsResponse.Quarter latest = earnings.quarters().getLast();
    if (latest.sources() == null || latest.sources().isEmpty()) {
      return Optional.empty();
    }
    if (latest.revenue() == null && latest.operatingProfit() == null && latest.netIncome() == null) {
      return Optional.empty();
    }
    var primarySource = latest.sources().getFirst();
    String basisLabel = "CFS".equals(latest.basis()) ? "연결" : "별도";
    String headline = String.format("%d년 %d분기 확정 실적 (%s)",
        latest.fiscalYear(), latest.quarter(), basisLabel);

    List<Metric> metrics = List.of(
        new Metric("매출액", formatWonString(latest.revenue()), false),
        new Metric("영업이익", formatWonString(latest.operatingProfit()), true),
        new Metric("당기순이익", formatWonString(latest.netIncome()), false)
    );
    List<String> tags = List.of("확정실적", basisLabel, latest.fiscalYear() + "Q" + latest.quarter());
    return Optional.of(new CardDraft(DisclosureCardCategory.CONFIRMED_EARNINGS, headline,
        primarySource.receivedDate(), metrics, tags));
  }

  public Optional<CardDraft> evaluateTreasuryStockAcquisition(String companyName,
      Map<String, String> dartRow, LocalDate publishedDate) {
    BigDecimal plannedAmount = DartDocumentTableParser.parseNumber(dartRow.get("aqplnprc_ostk"))
        .or(() -> DartDocumentTableParser.parseNumber(dartRow.get("aqplnprc_estk")))
        .orElse(null);
    BigDecimal plannedShares = DartDocumentTableParser.parseNumber(dartRow.get("aqpln_stk_ostk"))
        .orElse(null);
    String purpose = dartRow.getOrDefault("aq_pp", "");
    boolean cancellation = purpose.contains("소각");

    if (!cancellation && (plannedAmount == null || plannedAmount.compareTo(MIN_TREASURY_WON) < 0)) {
      return Optional.empty();
    }

    String amountText = plannedAmount != null ? formatWon(plannedAmount) : "-";
    String sharesText = plannedShares != null ? String.format("%,d주", plannedShares.longValue()) : "-";
    String purposeShort = cancellation ? "주주가치 제고 (소각)" : (purpose.isBlank() ? "주주가치 제고" : truncate(purpose, 18));
    String headline = cancellation
        ? String.format("자기주식 %s 취득 및 소각 결정", amountText)
        : String.format("자기주식 %s 취득 결정", amountText);

    List<Metric> metrics = List.of(
        new Metric("취득예정금액", amountText, true),
        new Metric("취득예정주식", sharesText, false),
        new Metric("취득목적", purposeShort, false)
    );
    List<String> tags = cancellation
        ? List.of("주주환원", "자사주 소각")
        : List.of("주주환원", "자사주 취득");
    return Optional.of(new CardDraft(DisclosureCardCategory.SHAREHOLDER_RETURN, headline,
        publishedDate, metrics, tags));
  }

  public Optional<CardDraft> evaluateShareholderDocument(String companyName, String reportTitle,
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
      return Optional.of(new CardDraft(DisclosureCardCategory.SHAREHOLDER_RETURN, headline,
          publishedDate, metrics, List.of("주주환원", "자사주 소각")));
    }

    // 현금·현물배당결정
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
    return Optional.of(new CardDraft(DisclosureCardCategory.SHAREHOLDER_RETURN, headline,
        publishedDate, metrics, List.of("주주환원", "현금배당")));
  }

  public Optional<CardDraft> evaluateSemiconductorCapex(String companyName, String indutyCode,
      String reportTitle, LocalDate publishedDate, ParsedDocument doc) {
    if (!isSemiconductorIndustry(indutyCode)) {
      return Optional.empty();
    }
    BigDecimal investAmount = doc.findValue("투자금액", "투자내역투자금액")
        .flatMap(DartDocumentTableParser::parseNumber).orElse(null);
    BigDecimal equityRatio = doc.findValue("자기자본대비")
        .flatMap(DartDocumentTableParser::parseNumber).orElse(null);
    String purpose = doc.findValue("투자목적", "투자대상").orElse("반도체 설비 증설");
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
    String headline = String.format("반도체 신규시설투자 %s 결정 — %s", amountText, truncate(purpose, 24));

    List<Metric> metrics = List.of(
        new Metric("투자금액", amountText, false),
        new Metric("자기자본대비", ratioText, true),
        new Metric("투자기간", periodText, false)
    );
    List<String> tags = List.of("반도체 CAPEX", indutyCode.startsWith("29271") ? "반도체 장비" : "반도체 제조");
    return Optional.of(new CardDraft(DisclosureCardCategory.SEMICONDUCTOR_CAPEX, headline,
        publishedDate, metrics, tags));
  }

  private String buildPreliminaryHeadline(String companyName, String reportTitle,
      BigDecimal opYoy, String plainText) {
    if (plainText.contains("흑자전환")) {
      return "잠정 실적 발표 — 영업이익 흑자전환";
    }
    if (plainText.contains("적자전환")) {
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
          List<String> numericCells = row.stream().skip(1)
              .filter(cell -> DartDocumentTableParser.parseNumber(cell).isPresent())
              .toList();
          return numericCells.size() >= 2 ? Optional.of(numericCells.getLast()) : Optional.empty();
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

  public static String formatWonString(String raw) {
    if (raw == null || raw.isBlank()) {
      return "-";
    }
    return DartDocumentTableParser.parseNumber(raw)
        .map(DisclosureCardRuleEngine::formatWon)
        .orElse(raw);
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

  public record CardDraft(
      DisclosureCardCategory category,
      String headline,
      LocalDate publishedDate,
      List<Metric> metrics,
      List<String> tags) {
  }
}
