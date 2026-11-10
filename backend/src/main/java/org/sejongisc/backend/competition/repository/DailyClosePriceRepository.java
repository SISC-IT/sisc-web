package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface DailyClosePriceRepository extends JpaRepository<DailyClosePrice, UUID> {

    // 지금 거래 가능한 최신 종가와 직전 거래일 종가 (최대 2건)
    // 공개 여부는 availableDate로 거르고, 순서는 priceDate로 정한다. 여러 날짜를 한 번에 적재하면 availableDate가 같아질 수 있다.
    // 인덱스는 uk_daily_close_price_stock_date (stock_id, price_date)를 그대로 쓴다.
    List<DailyClosePrice> findTop2ByStockAndAvailableDateLessThanEqualOrderByPriceDateDesc(
            CompetitionStock stock, LocalDateTime now);

    boolean existsByStockAndPriceDate(CompetitionStock stock, LocalDate priceDate);
}
