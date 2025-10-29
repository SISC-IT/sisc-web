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
    public void dailyOpenScheduler() {
        bettingService.createBetRound(Scope.DAILY);
    }

    @Scheduled(cron = "0 0 9 * * MON")
    public void weeklyOpenScheduler() {
        bettingService.createBetRound(Scope.WEEKLY);
    }

    @Scheduled(cron = "0 0 22 * * MON-FRI")
    public void dailyCloseScheduler() {
        bettingService.closeBetRound();
    }

    @Scheduled(cron = "0 0 22 * * FRI")
    public void weeklyCloseScheduler() {
        bettingService.closeBetRound();
    }

    @Scheduled(cron = "0 5 22 * * MON-FRI")
    public void dailySettleScheduler() {
        bettingService.settleUserBets();
    }

    @Scheduled(cron = "0 5 22 * * FRI")
    public void weeklySettleScheduler() {
        bettingService.settleUserBets();
    }

}
