package org.sejongisc.backend.disclosure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sejongisc.backend.disclosure.client.OpenDartClient;
import org.sejongisc.backend.disclosure.client.OpenDartClient.DartCompany;
import org.sejongisc.backend.disclosure.client.OpenDartClient.DartDisclosurePage;
import org.sejongisc.backend.disclosure.client.OpenDartClient.DartListResponse;
import org.sejongisc.backend.disclosure.repository.DisclosureCompanyRepository;
import org.sejongisc.backend.disclosure.repository.DisclosureEventRepository;
import org.sejongisc.backend.disclosure.service.settings.DartDocumentTableParser;
import org.sejongisc.backend.disclosure.service.settings.DisclosureEventRuleEngine;
import org.springframework.transaction.support.TransactionOperations;

class DisclosureEventCollectionServiceTest {
  private static final String COMPANY = "00000001";
  private static final LocalDate DATE = LocalDate.of(2026, 11, 19);

  @ParameterizedTest
  @ValueSource(strings = {"000", "013", "020"})
  void checksExchangeStatusEvenWhenThereAreNoRows(String status) {
    var client = mock(OpenDartClient.class);
    var events = mock(DisclosureEventRepository.class);
    var service = new DisclosureEventCollectionService(client, new DartDocumentTableParser(),
        new DisclosureEventRuleEngine(), mock(DisclosureCompanyRepository.class), events,
        TransactionOperations.withoutTransaction(), new ObjectMapper());
    when(client.company(COMPANY)).thenReturn(new DartCompany("000", "12"));
    when(client.treasuryStockAcquisitions(COMPANY, DATE, DATE))
        .thenReturn(new DartListResponse("013", null, null));
    when(client.searchDisclosures(COMPANY, "I", DATE, DATE, 1, 100, false))
        .thenReturn(new DartDisclosurePage(status, null, 1, 100, 0, 0,
            "000".equals(status) ? List.of() : null));

    if ("020".equals(status)) {
      assertThatThrownBy(() -> service.collectForCompany(COMPANY, DATE, DATE))
          .isInstanceOf(IllegalStateException.class).hasMessageContaining("020");
    } else {
      assertThat(service.collectForCompany(COMPANY, DATE, DATE)).isZero();
    }
    verifyNoInteractions(events);
  }
}
