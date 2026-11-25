package org.sejongisc.backend.competition.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DepositInterestScheduler {

    private final DepositInterestService interestService;

    // 자정에 지급한다. 자정에 서버가 꺼져 있었거나 실패했으면 15:00에 밀린 밤을 지급한다.
    // 15:00은 16:00 순위 스냅샷 전이라 그날 순위에 이자가 반영된다. 이미 지급한 밤은 건너뛴다.
    @Scheduled(cron = "0 0 0,15 * * *", zone = "Asia/Seoul")
    public void payDailyInterest() {
        interestService.payDailyInterest();
    }
}
