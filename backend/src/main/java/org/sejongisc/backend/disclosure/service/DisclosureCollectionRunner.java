package org.sejongisc.backend.disclosure.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "disclosure.collection.run", havingValue = "true")
public class DisclosureCollectionRunner implements ApplicationRunner {
  private final DisclosureCollectionService collectionService;
  private final ConfigurableApplicationContext applicationContext;

  @Override
  public void run(ApplicationArguments args) {
    String corpCode = requiredOption(args, "corp-code");
    LocalDate from = LocalDate.parse(requiredOption(args, "from"));
    LocalDate to = LocalDate.parse(requiredOption(args, "to"));
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("--from must be before or equal to --to");
    }
    int maxPages = args.containsOption("max-pages")
        ? Integer.parseInt(args.getOptionValues("max-pages").getFirst()) : 5;

    try {
      int inserted = collectionService.collect(corpCode, from, to, maxPages);
      log.info("Disclosure collection finished: corpCode={}, from={}, to={}, inserted={}",
          corpCode, from, to, inserted);
      if (args.containsOption("financial-year") && args.containsOption("report-code")) {
        int year = Integer.parseInt(args.getOptionValues("financial-year").getFirst());
        String reportCode = args.getOptionValues("report-code").getFirst();
        String basis = args.containsOption("basis")
            ? args.getOptionValues("basis").getFirst() : "CFS";
        int metrics = collectionService.collectFinancialMetrics(corpCode, year, reportCode, basis);
        log.info("Financial metric collection finished: corpCode={}, year={}, reportCode={}, basis={}, inserted={}",
            corpCode, year, reportCode, basis, metrics);
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
