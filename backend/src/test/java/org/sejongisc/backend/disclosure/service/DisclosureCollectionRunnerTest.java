package org.sejongisc.backend.disclosure.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

class DisclosureCollectionRunnerTest {
  @Test
  void collectsQuarterlyEarningsAndClosesTheApplication() {
    var quarterly = mock(DisclosureEarningsCollectionService.class);
    var context = mock(ConfigurableApplicationContext.class);
    var through = LocalDate.of(2026, 11, 19);
    when(quarterly.collect("00000001", through)).thenReturn(new DisclosureEarningsCollectionService.CollectionResult(
        new DisclosureCollectionService.Counts(0, 0), List.of(), Optional.empty()));

    new DisclosureCollectionRunner(quarterly, context).run(
        new DefaultApplicationArguments("--corp-code=00000001", "--to=2026-11-19"));

    verify(quarterly).collect("00000001", through);
    verify(context).close();
  }
}
