package org.sejongisc.backend.disclosure.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.common.exception.controller.GlobalExceptionHandler;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyDetailResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyListItem;
import org.sejongisc.backend.disclosure.dto.DisclosureCompanyResponse.CompanyListResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Quarter;
import org.sejongisc.backend.disclosure.dto.DisclosureEventResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureEventResponse.Metric;
import org.sejongisc.backend.disclosure.entity.DisclosureEventCategory;
import org.sejongisc.backend.disclosure.service.DisclosureQueryService;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DisclosureControllerTest {
  private final DisclosureQueryService queryService = mock(DisclosureQueryService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new DisclosureController(queryService))
          .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
          .setControllerAdvice(new GlobalExceptionHandler()).build();

  @Test
  void listsTrackedCompaniesWithUnreadIndicator() throws Exception {
    var item = new CompanyListItem(
        "00164779", "SK하이닉스", "000660", "KOSPI", true,
        LocalDate.of(2026, 11, 18), 3L, true);
    when(queryService.listCompanies(null, "하이닉스", 0, 200))
        .thenReturn(new CompanyListResponse(List.of(item), 1, 0, 200, 1));

    mvc.perform(get("/api/disclosures/companies").param("q", "하이닉스"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.items[0].companyName").value("SK하이닉스"))
        .andExpect(jsonPath("$.items[0].hasUnread").value(true))
        .andExpect(jsonPath("$.items[0].trackCapex").value(true));
  }

  @Test
  void returnsUnifiedCompanyDetailWithEarningsTrendAndFilteredEvents() throws Exception {
    var q3 = new Quarter(2026, 3, "2026-09", "CFS", "KRW", "9007199254740993", "7200000000000", "5600000000000", List.of(), null, null);
    var ev = new DisclosureEventResponse(
        UUID.randomUUID(), "20261118000001", "CAPEX", "CAPEX",
        "00164779", "SK하이닉스", "000660", "KOSPI", "26110",
        "신규시설투자등", "신규시설투자 1.98조 결정",
        LocalDate.of(2026, 11, 18),
        List.of(new Metric("투자금액", "1.98조", false)),
        List.of("CAPEX"), "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20261118000001");

    var detail = new CompanyDetailResponse(
        "00164779", "SK하이닉스", "000660", "KOSPI", "26110", true,
        q3, List.of(q3), List.of(ev));

    when(queryService.companyDetail(null, "00164779", DisclosureEventCategory.CAPEX))
        .thenReturn(Optional.of(detail));

    mvc.perform(get("/api/disclosures/companies/{corpCode}", "00164779")
            .param("type", "CAPEX"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companyName").value("SK하이닉스"))
        .andExpect(jsonPath("$.latestQuarter.revenue").value("9007199254740993"))
        .andExpect(jsonPath("$.earningsTrend.length()").value(1))
        .andExpect(jsonPath("$.events[0].category").value("CAPEX"));
  }

  @Test
  void rejectsInvalidCorpCodeOrEventTypeAndReturnsNotFound() throws Exception {
    mvc.perform(get("/api/disclosures/companies/{corpCode}", "123"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/disclosures/companies/{corpCode}", "00164779")
            .param("type", "UNKNOWN_TYPE"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(queryService);

    when(queryService.companyDetail(null, "00126380", null)).thenReturn(Optional.empty());
    mvc.perform(get("/api/disclosures/companies/{corpCode}", "00126380"))
        .andExpect(status().isNotFound());
  }
}
