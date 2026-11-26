package org.sejongisc.backend.disclosure.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.sejongisc.backend.disclosure.entity.DisclosureEventCategory;
import org.sejongisc.backend.disclosure.service.settings.DartDocumentTableParser;
import org.sejongisc.backend.disclosure.service.settings.DisclosureEventRuleEngine;

class DisclosureEventRuleEngineTest {
  private final DartDocumentTableParser parser = new DartDocumentTableParser();
  private final DisclosureEventRuleEngine engine = new DisclosureEventRuleEngine();

  @Test
  void classifiesCapexOnlyWhenTrackCapexEnabledAndDefaultsSamsungAndHynix() {
    assertThat(DisclosureCompany.isDefaultCapexCorp("00126380")).isTrue(); // 삼성전자
    assertThat(DisclosureCompany.isDefaultCapexCorp("00164779")).isTrue(); // SK하이닉스
    assertThat(DisclosureCompany.isDefaultCapexCorp("00164742")).isFalse(); // 현대차 (추후 DB 플래그로 활성화)

    assertThat(engine.classifyExchangeTitle("신규시설투자등", true))
        .contains(DisclosureEventCategory.CAPEX);
    assertThat(engine.classifyExchangeTitle("신규시설투자등", false))
        .isEmpty();
    assertThat(engine.classifyExchangeTitle("[기재정정]신규시설투자등", true))
        .isEmpty();
  }

  @Test
  void evaluatesOrderContractWithIndustryAdjustedThresholds() {
    assertThat(engine.classifyExchangeTitle("단일판매ㆍ공급계약체결", false))
        .contains(DisclosureEventCategory.ORDER_CONTRACT);

    String mediumOrderHtml = """
        <table>
          <tr><td>계약내용</td><td>선박 2척 건조 계약</td></tr>
          <tr><td>계약금액(원)</td><td>50,000,000,000</td></tr>
          <tr><td>매출액대비(%)</td><td>12.0</td></tr>
          <tr><td>계약상대방</td><td>유럽 선주사</td></tr>
        </table>
        """;
    assertThat(engine.evaluateOrderContract("조선사", "31111", "단일판매ㆍ공급계약체결",
        LocalDate.of(2026, 11, 19), parser.parse(mediumOrderHtml))).isEmpty();

    var generalDraft = engine.evaluateOrderContract("장비사", "26110", "단일판매ㆍ공급계약체결",
        LocalDate.of(2026, 11, 19), parser.parse(mediumOrderHtml));
    assertThat(generalDraft).isPresent();
    assertThat(generalDraft.get().category()).isEqualTo(DisclosureEventCategory.ORDER_CONTRACT);
    assertThat(generalDraft.get().metrics().get(1).value()).isEqualTo("12.0%");
  }

  @Test
  void evaluatesPreliminaryEarningsWithYoyRule() {
    String html = """
        <html><body>
        <p>(단위 : 백만원)</p>
        <table>
          <tr><th>구분</th><th>당기실적</th><th>전년동기대비(%)</th></tr>
          <tr><td>매출액</td><td>18,400,000</td><td>19.0</td></tr>
          <tr><td>영업이익</td><td>7,200,000</td><td>34.2</td></tr>
          <tr><td>당기순이익</td><td>5,600,000</td><td>41.5</td></tr>
        </table>
        </body></html>
        """;
    var draft = engine.evaluatePreliminaryEarnings("SK하이닉스",
        "연결재무제표기준영업(잠정)실적(공정공시)", LocalDate.of(2026, 11, 18), parser.parse(html));

    assertThat(draft).isPresent();
    assertThat(draft.get().category()).isEqualTo(DisclosureEventCategory.PRELIMINARY_EARNINGS);
    assertThat(draft.get().headline()).contains("+34.2%");
    assertThat(draft.get().metrics()).hasSize(3);
  }

  @Test
  void usesOnlyTheOperatingProfitYoyCellAndReadsDartAmountTags() {
    var doc = parser.parse("""
        <DOCUMENT><BODY><TABLE>
          <TR><TH>구분</TH><TH>당기실적</TH><TH>전년동기금액</TH><TH>전년동기대비(%)</TH></TR>
          <TR><TD>영업이익</TD><TE>-3378</TE><TE>-44972</TE><TU>-</TU></TR>
          <TR><TD>법인세비용차감전</TD><TE>1669</TE><TE>100</TE><TU>1569</TU></TR>
          <TR><TD>당기순이익</TD><TE>2248</TE><TE>-190</TE><TU>흑자전환</TU></TR>
        </TABLE></BODY></DOCUMENT>
        """);
    var draft = engine.evaluatePreliminaryEarnings("예시", "잠정실적", LocalDate.of(2026, 11, 18), doc)
        .orElseThrow();

    assertThat(draft.metrics().get(1).value()).isEqualTo("-3,378원");
    assertThat(draft.metrics().get(2).value()).isEqualTo("2,248원");
    assertThat(draft.tags()).doesNotContain("흑자전환");
  }

  @Test
  void emptyConfidentialityFieldsDoNotBypassTheContractThreshold() {
    var doc = parser.parse("""
        <table>
          <tr><td>계약금액(원)</td><td>5,000,000,000</td></tr>
          <tr><td>매출액대비(%)</td><td>1</td></tr>
          <tr><td>공시유보 관련내용</td><td>유보사유</td><td>-</td></tr>
          <tr><td>유보기한</td><td>-</td></tr>
        </table>
        """);
    assertThat(engine.evaluateOrderContract("예시", "26110", "단일판매ㆍ공급계약체결",
        LocalDate.of(2026, 11, 19), doc)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
      '', '<tr><td colspan="4">단위: 백만원</td></tr>', '주당순이익(원)', '1억'
      '<p>(단위: 백만원)</p>', '', '주당순이익(원)', '1억'
      '<p>(단위: 천원)</p>', '<tr><td colspan="4">단위: 백만원</td></tr>', '주당순이익(원)', '1억'
      '', '<caption>(백만원)</caption>', '주당순이익(원)', '1억'
      '', '<tr><td colspan="4">(백만원)</td></tr>', '주당순이익(원)', '1억'
      '', '', '주당순이익 (천원)', '100원'
      """)
  void readsOnlyCurrentAmountsAndUsesTheirOwnTableUnit(
      String precedingUnit, String tableUnit, String rowLabel, String expectedProfit) {
    var doc = parser.parse("""
        <table><tr><td>단위 : 원</td><td>주가 300</td></tr></table>
        %s
        <table>
          %s
          <tr><th>구분</th><th>당기실적</th><th>전기실적</th><th>전년동기대비(%%)</th></tr>
          <tr><td>매출액</td><td>-</td><td>500</td><td>20</td></tr>
          <tr><td>영업이익</td><td>100</td><td>90</td><td>30</td></tr>
          <tr><td>당기순이익</td><td>0</td><td>20</td><td>-</td></tr>
          <tr><td>%s</td><td>5</td><td>4</td><td>25</td></tr>
        </table>
        """.formatted(precedingUnit, tableUnit, rowLabel));
    var draft = engine.evaluatePreliminaryEarnings("예시", "잠정실적",
        LocalDate.of(2026, 11, 18), doc).orElseThrow();

    assertThat(draft.metrics()).extracting(metric -> metric.value())
        .containsExactly("-", expectedProfit + " (+30.0%)", "0원");
  }

  @Test
  void usesTheTableDeclarationAboveTheTableInsteadOfAPerShareRowUnit() {
    var doc = parser.parse("""
        <p>(단위: 백만원)</p>
        <table>
          <tr><th>구분</th><th>당기실적</th><th>전기실적</th><th>전년동기대비(%)</th></tr>
          <tr><td>영업이익</td><td>100</td><td>90</td><td>30</td></tr>
          <tr><td>주당순이익(원)</td><td>850</td><td>830</td><td>-</td></tr>
        </table>
        """);

    var draft = engine.evaluatePreliminaryEarnings("예시", "잠정실적",
        LocalDate.of(2026, 11, 18), doc).orElseThrow();

    assertThat(draft.metrics().get(1).value()).isEqualTo("1억 (+30.0%)");
  }

  @Test
  void readsGroupedYoyColumnsFromAnActualDartFiling() throws IOException {
    try (var input = getClass().getResourceAsStream("/disclosure/dart-20251030800790-earnings.html")) {
      var doc = parser.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
      assertThat(doc.findRowContaining("영업이익").orElseThrow())
          .containsExactly("영업이익", "당해실적", "19,316", "58,741", "-67.12", "-", "44,658", "-56.75", "-");

      var draft = engine.evaluatePreliminaryEarnings("HS효성첨단소재", "잠정실적",
          LocalDate.of(2025, 10, 30), doc).orElseThrow();
      assertThat(draft.headline()).contains("-56.8%");
      assertThat(draft.metrics().get(1).value()).isEqualTo("193억 (-56.8%)");
      assertThat(draft.tags()).contains("영업이익 20%↓");
    }
  }

  @Test
  void usesTheOperatingProfitYoyTurnaroundCellInsteadOfTheQoqCell() {
    var doc = parser.parse("""
        <table>
          <tr><th>구분</th><th>당기실적</th><th colspan="2">전기대비</th><th colspan="2">전년동기대비</th></tr>
          <tr><td>영업이익</td><td>100</td><td>-</td><td>적자전환</td><td>-</td><td>흑자전환</td></tr>
          <tr><td>법인세비용차감전계속사업이익</td><td>50</td><td>-</td><td>-</td><td>-</td><td>-</td></tr>
        </table>
        """);
    var draft = engine.evaluatePreliminaryEarnings("예시", "잠정실적", LocalDate.of(2026, 11, 18), doc)
        .orElseThrow();

    assertThat(draft.headline()).endsWith("영업이익 흑자전환");
    assertThat(draft.metrics().get(1).value()).isEqualTo("100원 (흑자전환)");
    assertThat(draft.metrics().get(2).value()).isEqualTo("-");
    assertThat(draft.tags()).contains("흑자전환").doesNotContain("적자전환");
  }

  @Test
  void aConfidentialityDeadlineWithoutAReasonDoesNotBypassThresholds() {
    var doc = parser.parse("""
        <table>
          <tr><td>계약금액(원)</td><td>5,000,000,000</td></tr>
          <tr><td>유보기한</td><td>2026-12-31</td></tr>
        </table>
        """);
    assertThat(engine.evaluateOrderContract("예시", "26110", "단일판매ㆍ공급계약체결",
        LocalDate.of(2026, 11, 19), doc)).isEmpty();
  }

  @Test
  void evaluatesCapexWhenTrackedAndFiltersSmallInvestments() {
    String largeCapexHtml = """
        <table>
          <tr><td>투자금액(원)</td><td>1,980,000,000,000</td></tr>
          <tr><td>자기자본대비(%)</td><td>19.8</td></tr>
          <tr><td>투자목적</td><td>HBM 신규 생산라인 증설</td></tr>
          <tr><td>시작일</td><td>2026-12-01</td></tr>
          <tr><td>종료일</td><td>2028-06-30</td></tr>
        </table>
        """;
    var largeDraft = engine.evaluateCapex("SK하이닉스", true,
        "신규시설투자등", LocalDate.of(2026, 11, 17), parser.parse(largeCapexHtml));
    assertThat(largeDraft).isPresent();
    assertThat(largeDraft.get().category()).isEqualTo(DisclosureEventCategory.CAPEX);
    assertThat(largeDraft.get().metrics().get(1).value()).isEqualTo("19.8%");

    assertThat(engine.evaluateCapex("현대차", false,
        "신규시설투자등", LocalDate.of(2026, 11, 17), parser.parse(largeCapexHtml))).isEmpty();
  }

  @Test
  void evaluatesShareholderReturnTreasuryAndDividend() {
    var treasury = engine.evaluateTreasuryStockAcquisition("KB금융",
        Map.of("aqpln_prc_ostk", "400000000000", "aqpln_stk_ostk", "5000000", "aq_pp", "주주가치 제고 및 소각"),
        LocalDate.of(2026, 11, 16));
    assertThat(treasury).isPresent();
    assertThat(treasury.get().headline()).contains("4,000억");
    assertThat(treasury.get().tags()).contains("자사주 소각");

    String dividendHtml = """
        <table>
          <tr><td>1주당 배당금(원)</td><td>2,500</td></tr>
          <tr><td>시가배당율(%)</td><td>5.4</td></tr>
          <tr><td>배당금총액(원)</td><td>950,000,000,000</td></tr>
        </table>
        """;
    var dividend = engine.evaluateShareholderDocument("KB금융", "현금ㆍ현물배당결정",
        LocalDate.of(2026, 11, 16), parser.parse(dividendHtml));
    assertThat(dividend).isPresent();
    assertThat(dividend.get().metrics().get(1).value()).isEqualTo("5.4%");
  }
}
