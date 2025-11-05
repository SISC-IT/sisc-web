package org.sejongisc.backend.betting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.betting.entity.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class BettingScheduler {

    private final BettingService bettingService;

    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Seoul")
    public void dailyOpenScheduler() {
        bettingService.createBetRound(Scope.DAILY);
//        log.info("✅ 스케줄러 정상 작동 중: {}", LocalDateTime.now());
    }

    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")
    public void weeklyOpenScheduler() {
        bettingService.createBetRound(Scope.WEEKLY);
    }

    @Scheduled(cron = "0 0 22 * * MON-FRI", zone = "Asia/Seoul")
    public void dailyCloseScheduler() {
        bettingService.closeBetRound();
    }

    @Scheduled(cron = "0 0 22 * * FRI", zone = "Asia/Seoul")
    public void weeklyCloseScheduler() {
        bettingService.closeBetRound();
    }

    @Scheduled(cron = "0 5 22 * * MON-FRI", zone = "Asia/Seoul")
    public void dailySettleScheduler() {
        bettingService.settleUserBets();
    }

    @Scheduled(cron = "0 5 22 * * FRI", zone = "Asia/Seoul")
    public void weeklySettleScheduler() {
        bettingService.settleUserBets();
    }

}
