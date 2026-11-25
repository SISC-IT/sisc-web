package org.sejongisc.backend.competition.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompetitionEndScheduler {

    private final DepositInterestService interestService;
    private final AccountSnapshotService snapshotService;
    private final CompetitionService competitionService;

    // 이자 지급은 ACTIVE 대회만 보므로 닫기 전에 한다. 트랜잭션을 나눠야 대회 저장이 지급 기록을 덮어쓰지 않는다.
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void endDueCompetitions() {
        interestService.payDailyInterest();
        snapshotService.createFinalSnapshot();
        competitionService.endDueCompetitions();
    }
}
