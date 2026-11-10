package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface DailyClosePriceRepository extends JpaRepository<DailyClosePrice, UUID> {

    // 지금 거래 가능한 최신 종가와 직전 거래일 종가 (최신순 최대 2건)
    List<DailyClosePrice> findTop2ByStockAndAvailableDateLessThanEqualOrderByAvailableDateDesc(
            CompetitionStock stock, LocalDateTime now);

    boolean existsByStockAndPriceDate(CompetitionStock stock, LocalDate priceDate);
}
