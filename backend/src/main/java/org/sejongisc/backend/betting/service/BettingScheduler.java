package org.sejongisc.backend.betting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.betting.entity.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Component
@Slf4j
@RequiredArgsConstructor
public class BettingScheduler {

    private final BettingService bettingService;

    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Seoul")
    public void dailyOpenScheduler() {
        // 일일 라운드 생성
        bettingService.createBetRound(Scope.DAILY);

        // 월요일: 주간 라운드 생성
        if (LocalDate.now().getDayOfWeek() == DayOfWeek.MONDAY) {
            bettingService.createBetRound(Scope.WEEKLY);
        }
    }

    @Scheduled(cron = "0 0 22 * * MON-FRI", zone = "Asia/Seoul")
    public void closeScheduler() {
        bettingService.closeBetRound();
    }

    @Scheduled(cron = "0 5 22 * * MON-FRI", zone = "Asia/Seoul")
    public void settleScheduler() {
        bettingService.settleUserBets();
    }

}
