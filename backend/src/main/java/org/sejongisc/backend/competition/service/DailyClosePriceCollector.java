package org.sejongisc.backend.competition.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.repository.CompetitionStockRepository;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyClosePriceCollector {

    private final DailyClosePriceWriter writer;
    private final CompetitionStockRepository stockRepository;
    private final Clock clock;

    public CollectionResult collectDomestic() {
        LocalDate baseDate = LocalDate.now(clock);
        List<CompetitionStock> stocks = stockRepository.findByMarket(Market.KR);

        if (stocks.isEmpty()) {
            log.warn("[종가수집] 수집 대상 종목이 없습니다: market=KR");
            return new CollectionResult(0, 0, 0);
        }

        int saved = 0;
        int skipped = 0;
        int failed = 0;

        for (CompetitionStock stock : stocks) {
            try {
                if (writer.saveIfAbsent(stock, baseDate)) {
                    saved++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("[종가수집] 종목 수집 실패: ticker={}, baseDate={}, message={}",
                        stock.getTicker(), baseDate, e.getMessage());
            }
        }

        log.info("[종가수집] 완료: baseDate={}, 대상={}, 저장={}, 건너뜀={}, 실패={}",
                baseDate, stocks.size(), saved, skipped, failed);
        return new CollectionResult(saved, skipped, failed);
    }

    public record CollectionResult(int saved, int skipped, int failed) {}
}
