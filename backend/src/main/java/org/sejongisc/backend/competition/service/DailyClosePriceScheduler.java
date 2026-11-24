package org.sejongisc.backend.competition.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 장 마감(15:30) 이후, 주문 접수 시작(16:30) 전에 수집한다. */
@Component
@RequiredArgsConstructor
public class DailyClosePriceScheduler {

    private final DailyClosePriceCollector collector;
    private final DepositInterestService interestService;
    private final AccountSnapshotService snapshotService;
    private final CompetitionService competitionService;

    @Scheduled(
            cron = "${competition.price-collection.cron:0 0 16 * * MON-FRI}",
            zone = "Asia/Seoul",
            scheduler = "closePriceScheduler"
    )
    public void collectDomesticClosePrices() {
        if (!competitionService.hasOpenCompetition()) {
            return;
        }
        collector.collectDomestic();
        // 00:00, 15:00 지급을 모두 놓쳤어도 스냅샷 전에 지급한다
        interestService.payDailyInterest();
        snapshotService.createDailySnapshot();
        competitionService.endDueCompetitions();
    }
}
