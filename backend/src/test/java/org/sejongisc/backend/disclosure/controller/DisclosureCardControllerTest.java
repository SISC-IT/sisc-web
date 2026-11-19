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
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse.Metric;
import org.sejongisc.backend.disclosure.dto.DisclosureCardResponse.PageResponse;
import org.sejongisc.backend.disclosure.entity.DisclosureCardCategory;
import org.sejongisc.backend.disclosure.service.DisclosureCardQueryService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DisclosureCardControllerTest {
  private final DisclosureCardQueryService queryService = mock(DisclosureCardQueryService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new DisclosureCardController(queryService))
          .setControllerAdvice(new GlobalExceptionHandler()).build();

  @Test
  void listsDisclosureCardsFilteredByCategoryAndKeyword() throws Exception {
    UUID id = UUID.randomUUID();
    var card = new DisclosureCardResponse(
        id, "20261118000001", "SEMICONDUCTOR_CAPEX", "반도체 CAPEX",
        "00164779", "SK하이닉스", "000660", "KOSPI", "26110",
        "신규시설투자등", "반도체 신규시설투자 1.98조 결정",
        LocalDate.of(2026, 11, 18),
        List.of(new Metric("투자금액", "1.98조", false)),
        List.of("반도체 CAPEX"), "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20261118000001");

    when(queryService.list(DisclosureCardCategory.SEMICONDUCTOR_CAPEX, "하이닉스", "latest", 0, 12))
        .thenReturn(new PageResponse(List.of(card), 1, 0, 12, 1));

    mvc.perform(get("/api/disclosures/cards")
            .param("category", "SEMICONDUCTOR_CAPEX")
            .param("q", "하이닉스"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.items[0].companyName").value("SK하이닉스"));
  }

  @Test
  void rejectsInvalidCategoryAndReturnsNotFoundForUnknownCard() throws Exception {
    mvc.perform(get("/api/disclosures/cards").param("category", "UNKNOWN_TYPE"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(queryService);

    UUID missingId = UUID.randomUUID();
    when(queryService.findById(missingId)).thenReturn(Optional.empty());
    mvc.perform(get("/api/disclosures/cards/{cardId}", missingId))
        .andExpect(status().isNotFound());
  }
}
