package org.sejongisc.backend.competition.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 시작일이 된 대회를 연다. 주문은 16:30부터라 매시 실행하면 자정 실행을 놓쳐도 주문 전에 열린다. */
@Component
@RequiredArgsConstructor
public class CompetitionStartScheduler {

    private final CompetitionService competitionService;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void startDueCompetitions() {
        competitionService.startDueCompetitions();
    }
}
