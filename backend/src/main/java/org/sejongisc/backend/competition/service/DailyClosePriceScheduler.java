package org.sejongisc.backend.competition.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 장 마감(15:30) 이후, 주문 접수 시작(16:30) 전에 수집한다. */
@Component
@RequiredArgsConstructor
public class DailyClosePriceScheduler {

    private final DailyClosePriceCollector collector;

    @Scheduled(cron = "${competition.price-collection.cron:0 0 16 * * MON-FRI}", zone = "Asia/Seoul")
    public void collectDomesticClosePrices() {
        collector.collectDomestic();
    }
}
