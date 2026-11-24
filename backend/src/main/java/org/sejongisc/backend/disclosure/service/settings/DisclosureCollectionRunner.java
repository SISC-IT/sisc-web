package org.sejongisc.backend.disclosure.service.settings;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.disclosure.dto.DisclosureEarningsResponse.Source;
import org.sejongisc.backend.disclosure.service.DisclosureEarningsCollectionService;
import org.sejongisc.backend.disclosure.service.DisclosureEventCollectionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

// 공시 단건 수집 CLI 실행기
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "disclosure.collection.run", havingValue = "true")
public class DisclosureCollectionRunner implements ApplicationRunner {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final DisclosureEarningsCollectionService earningsCollectionService;
  private final DisclosureEventCollectionService eventCollectionService;
  private final ConfigurableApplicationContext applicationContext;

  @Override
  public void run(ApplicationArguments args) {
    try {
      String corpCode = requiredOption(args, "corp-code");
      LocalDate through = args.containsOption("to")
          ? LocalDate.parse(requiredOption(args, "to"))
          : LocalDate.now(SEOUL);

      var result = earningsCollectionService.collect(corpCode, through);
      result.earnings().ifPresentOrElse(earnings -> {
        for (var quarter : earnings.quarters()) {
          log.info("Quarterly earnings: corpCode={}, fiscalYear={}, quarter={}, periodEnd={}, "
                  + "basis={}, currency={}, revenue={}, operatingProfit={}, netIncome={}, sources={}",
              corpCode, quarter.fiscalYear(), quarter.quarter(), quarter.periodEnd(), quarter.basis(),
              quarter.currency(), quarter.revenue(), quarter.operatingProfit(), quarter.netIncome(),
              quarter.sources().stream().map(Source::url).toList());
        }
      }, () -> log.info("No regular reports: corpCode={}, through={}", corpCode, through));

      log.info("Quarterly earnings collection: corpCode={}, filingsInserted={}, metricsInserted={}",
          corpCode, result.counts().filingsInserted(), result.counts().metricsInserted());
      if (!result.pendingPeriods().isEmpty()) {
        log.warn("Quarterly earnings pending: corpCode={}, periods={}", corpCode, result.pendingPeriods());
      }

      if (eventCollectionService != null && !args.containsOption("skip-events")) {
        int eventsInserted = eventCollectionService.collectForCompany(
            corpCode, through.minusYears(1), through);
        log.info("Disclosure events collection: corpCode={}, eventsInserted={}",
            corpCode, eventsInserted);
      }
    } finally {
      applicationContext.close();
    }
  }

  private String requiredOption(ApplicationArguments args, String name) {
    if (!args.containsOption(name) || args.getOptionValues(name).isEmpty()) {
      throw new IllegalArgumentException("Missing required option --" + name);
    }
    return args.getOptionValues(name).getFirst();
  }
}
