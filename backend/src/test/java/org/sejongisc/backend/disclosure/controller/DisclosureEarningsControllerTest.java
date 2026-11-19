package org.sejongisc.backend.disclosure.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.common.exception.controller.GlobalExceptionHandler;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Quarter;
import org.sejongisc.backend.disclosure.service.DisclosureEarningsQueryService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DisclosureEarningsControllerTest {
  private final DisclosureEarningsQueryService query = mock(DisclosureEarningsQueryService.class);
  private final String route = "/api/disclosures/companies/{corpCode}/financials";
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new DisclosureEarningsController(query))
          .setControllerAdvice(new GlobalExceptionHandler()).build();

  @Test
  void readsStoredEarningsAndPreservesMonetaryPrecision() throws Exception {
    var quarter = new Quarter(2026, 3, "2026-09", "CFS", "KRW", "9007199254740993", "0", null, List.of());
    when(query.latest("00000001")).thenReturn(Optional.of(
        new DisclosureEarningsResponse("00000001", "Fixture Company", null, List.of(quarter))));
    mvc.perform(get(route, "00000001")).andExpect(status().isOk())
        .andExpect(jsonPath("$.quarters[0].revenue").value("9007199254740993"))
        .andExpect(jsonPath("$.quarters[0].operatingProfit").value("0"));
  }

  @Test
  void returnsBadRequestForInvalidCompanyCodeAndNotFoundForAnUnknownCompany() throws Exception {
    mvc.perform(get(route, "123456")).andExpect(status().isBadRequest());
    verifyNoInteractions(query);
    when(query.latest("00000001")).thenReturn(Optional.empty());
    mvc.perform(get(route, "00000001")).andExpect(status().isNotFound());
  }
}
