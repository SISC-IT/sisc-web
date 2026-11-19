package org.sejongisc.backend.competition.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.sejongisc.backend.assetmanagement.model.DailyCandle;
import org.sejongisc.backend.assetmanagement.service.MarketDataClient;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.repository.DailyClosePriceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 종목마다 트랜잭션을 분리해 한 종목의 실패가 이미 저장된 다른 종목을 롤백시키지 않게 한다. */
@Service
public class DailyClosePriceWriter {

    private final MarketDataClient marketDataClient;
    private final DailyClosePriceRepository closePriceRepository;
    private final LocalTime availableAt;

    public DailyClosePriceWriter(
            MarketDataClient marketDataClient,
            DailyClosePriceRepository closePriceRepository,
            @Value("${competition.price-collection.available-at:16:30}") LocalTime availableAt
    ) {
        this.marketDataClient = marketDataClient;
        this.closePriceRepository = closePriceRepository;
        this.availableAt = availableAt;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveIfAbsent(CompetitionStock stock, LocalDate baseDate) {
        if (closePriceRepository.existsByStockAndPriceDate(stock, baseDate)) {
            return false;
        }

        DailyCandle candle = findCandle(stock.getTicker(), baseDate);
        if (candle == null) {
            // 휴장일이면 키움이 해당 일자 캔들을 주지 않는다.
            return false;
        }

        closePriceRepository.save(DailyClosePrice.builder()
                .stock(stock)
                .priceDate(baseDate)
                .availableAt(LocalDateTime.of(baseDate, availableAt))
                .closePrice(candle.closePrice())
                .build());
        return true;
    }

    /** 키움은 기준일부터 과거 방향으로 여러 건을 주므로 날짜가 일치하는 것만 쓴다. */
    private DailyCandle findCandle(String ticker, LocalDate baseDate) {
        List<DailyCandle> candles = marketDataClient.fetchDomesticDailyCandles(ticker, baseDate);
        return candles.stream()
                .filter(candle -> baseDate.equals(candle.date()))
                .findFirst()
                .orElse(null);
    }
}
