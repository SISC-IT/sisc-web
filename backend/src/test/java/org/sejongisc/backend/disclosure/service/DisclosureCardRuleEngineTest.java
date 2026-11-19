package org.sejongisc.backend.disclosure.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Quarter;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Source;
import org.sejongisc.backend.disclosure.entity.DisclosureCardCategory;

class DisclosureCardRuleEngineTest {
  private final DartDocumentTableParser parser = new DartDocumentTableParser();
  private final DisclosureCardRuleEngine engine = new DisclosureCardRuleEngine();

  @Test
  void classifiesSemiconductorCapexOnlyForSemiconductorIndustryCodes() {
    assertThat(engine.isSemiconductorIndustry("26110")).isTrue();
    assertThat(engine.isSemiconductorIndustry("29271")).isTrue();
    assertThat(engine.isSemiconductorIndustry("31111")).isFalse(); // 조선

    assertThat(engine.classifyExchangeTitle("신규시설투자등", "26110"))
        .contains(DisclosureCardCategory.SEMICONDUCTOR_CAPEX);
    assertThat(engine.classifyExchangeTitle("신규시설투자등", "31111"))
        .isEmpty();
    assertThat(engine.classifyExchangeTitle("[기재정정]신규시설투자등", "26110"))
        .isEmpty();
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
    var doc = parser.parse(html);
    var draft = engine.evaluatePreliminaryEarnings("SK하이닉스",
        "연결재무제표기준영업(잠정)실적(공정공시)", LocalDate.of(2026, 11, 18), doc);

    assertThat(draft).isPresent();
    assertThat(draft.get().category()).isEqualTo(DisclosureCardCategory.PRELIMINARY_EARNINGS);
    assertThat(draft.get().headline()).contains("+34.2%");
    assertThat(draft.get().metrics()).hasSize(3);
  }

  @Test
  void evaluatesSemiconductorCapexAndFiltersSmallInvestments() {
    String largeCapexHtml = """
        <table>
          <tr><td>투자금액(원)</td><td>1,980,000,000,000</td></tr>
          <tr><td>자기자본대비(%)</td><td>19.8</td></tr>
          <tr><td>투자목적</td><td>HBM 신규 생산라인 증설</td></tr>
          <tr><td>시작일</td><td>2026-12-01</td></tr>
          <tr><td>종료일</td><td>2028-06-30</td></tr>
        </table>
        """;
    var largeDraft = engine.evaluateSemiconductorCapex("SK하이닉스", "26110",
        "신규시설투자등", LocalDate.of(2026, 11, 17), parser.parse(largeCapexHtml));
    assertThat(largeDraft).isPresent();
    assertThat(largeDraft.get().category()).isEqualTo(DisclosureCardCategory.SEMICONDUCTOR_CAPEX);
    assertThat(largeDraft.get().metrics().get(1).value()).isEqualTo("19.8%");

    String tinyCapexHtml = """
        <table>
          <tr><td>투자금액(원)</td><td>5,000,000,000</td></tr>
          <tr><td>자기자본대비(%)</td><td>1.2</td></tr>
        </table>
        """;
    assertThat(engine.evaluateSemiconductorCapex("SK하이닉스", "26110",
        "신규시설투자등", LocalDate.of(2026, 11, 17), parser.parse(tinyCapexHtml))).isEmpty();
  }

  @Test
  void evaluatesShareholderReturnTreasuryAndDividend() {
    var treasury = engine.evaluateTreasuryStockAcquisition("KB금융",
        Map.of("aqplnprc_ostk", "400000000000", "aqpln_stk_ostk", "5000000", "aq_pp", "주주가치 제고 및 소각"),
        LocalDate.of(2026, 11, 16));
    assertThat(treasury).isPresent();
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

  @Test
  void evaluatesConfirmedEarningsFromExistingResponse() {
    var source = new Source("20261114000123", LocalDate.of(2026, 11, 14),
        "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20261114000123");
    var quarter = new Quarter(2026, 3, "2026-09", "CFS", "KRW",
        "64200000000", "31500000000", "28900000000", List.of(source));
    var response = new DisclosureEarningsResponse("00123456", "알테오젠", "196170", List.of(quarter));

    var draft = engine.evaluateConfirmedEarnings(response);
    assertThat(draft).isPresent();
    assertThat(draft.get().category()).isEqualTo(DisclosureCardCategory.CONFIRMED_EARNINGS);
    assertThat(draft.get().metrics().get(0).value()).isEqualTo("642억");
  }
}
