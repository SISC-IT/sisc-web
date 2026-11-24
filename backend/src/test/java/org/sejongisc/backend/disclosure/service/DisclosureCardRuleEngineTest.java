package org.sejongisc.backend.disclosure.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.disclosure.entity.DisclosureCardCategory;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.sejongisc.backend.disclosure.service.settings.DartDocumentTableParser;
import org.sejongisc.backend.disclosure.service.settings.DisclosureCardRuleEngine;

class DisclosureCardRuleEngineTest {
  private final DartDocumentTableParser parser = new DartDocumentTableParser();
  private final DisclosureCardRuleEngine engine = new DisclosureCardRuleEngine();

  @Test
  void classifiesCapexOnlyWhenTrackCapexEnabledAndDefaultsSamsungAndHynix() {
    assertThat(DisclosureCompany.isDefaultCapexCorp("00126380")).isTrue(); // 삼성전자
    assertThat(DisclosureCompany.isDefaultCapexCorp("00164779")).isTrue(); // SK하이닉스
    assertThat(DisclosureCompany.isDefaultCapexCorp("00164742")).isFalse(); // 현대차 (추후 DB 플래그로 활성화)

    assertThat(engine.classifyExchangeTitle("신규시설투자등", true))
        .contains(DisclosureCardCategory.CAPEX);
    assertThat(engine.classifyExchangeTitle("신규시설투자등", false))
        .isEmpty();
    assertThat(engine.classifyExchangeTitle("[기재정정]신규시설투자등", true))
        .isEmpty();
  }

  @Test
  void evaluatesOrderContractWithIndustryAdjustedThresholds() {
    assertThat(engine.classifyExchangeTitle("단일판매ㆍ공급계약체결", false))
        .contains(DisclosureCardCategory.ORDER_CONTRACT);

    String mediumOrderHtml = """
        <table>
          <tr><td>계약내용</td><td>선박 2척 건조 계약</td></tr>
          <tr><td>계약금액(원)</td><td>50,000,000,000</td></tr>
          <tr><td>매출액대비(%)</td><td>12.0</td></tr>
          <tr><td>계약상대방</td><td>유럽 선주사</td></tr>
        </table>
        """;
    // 조선업(311xx, 수주 다빈도 업종)은 15% 미만 & 1,000억 미만이면 필터링
    assertThat(engine.evaluateOrderContract("조선사", "31111", "단일판매ㆍ공급계약체결",
        LocalDate.of(2026, 11, 19), parser.parse(mediumOrderHtml))).isEmpty();

    // 일반 업종(261xx)은 10% 이상이므로 통과
    var generalDraft = engine.evaluateOrderContract("장비사", "26110", "단일판매ㆍ공급계약체결",
        LocalDate.of(2026, 11, 19), parser.parse(mediumOrderHtml));
    assertThat(generalDraft).isPresent();
    assertThat(generalDraft.get().category()).isEqualTo(DisclosureCardCategory.ORDER_CONTRACT);
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
    assertThat(draft.get().category()).isEqualTo(DisclosureCardCategory.PRELIMINARY_EARNINGS);
    assertThat(draft.get().headline()).contains("+34.2%");
    assertThat(draft.get().metrics()).hasSize(3);
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
    assertThat(largeDraft.get().category()).isEqualTo(DisclosureCardCategory.CAPEX);
    assertThat(largeDraft.get().metrics().get(1).value()).isEqualTo("19.8%");

    assertThat(engine.evaluateCapex("현대차", false,
        "신규시설투자등", LocalDate.of(2026, 11, 17), parser.parse(largeCapexHtml))).isEmpty();
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
}
