package org.sejongisc.backend.betting.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.betting.entity.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BettingScheduler {

    private final BettingService bettingService;

    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void dailyScheduler() {
        bettingService.createBetRound(Scope.DAILY);
    }

    @Scheduled(cron = "0 0 9 * * MON")
    public void weeklyScheduler() {
        bettingService.createBetRound(Scope.WEEKLY);
    }
}
