package org.sejongisc.backend.competition.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompetitionStockSyncScheduler {

    private final CompetitionStockSyncService syncService;
    private final CompetitionService competitionService;

    @Scheduled(cron = "${competition.stock-sync.cron:0 40 15 * * MON-FRI}", zone = "Asia/Seoul")
    public void syncDomesticStocks() {
        if (!competitionService.hasOpenCompetition()) {
            return;
        }
        syncService.syncDomestic();
    }
}
