package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DailyClosePriceRepository extends JpaRepository<DailyClosePrice, UUID> {

    // 지금 거래 가능한 최신 종가와 직전 거래일 종가 (최대 2건)
    // 공개 여부는 availableAt으로 거르고, 순서는 priceDate로 정한다. 여러 날짜를 한 번에 적재하면 availableAt이 같아질 수 있다.
    // 인덱스는 uk_daily_close_price_stock_date (stock_id, price_date)를 그대로 쓴다.
    List<DailyClosePrice> findTop2ByStockAndAvailableAtLessThanEqualOrderByPriceDateDesc(
            CompetitionStock stock, LocalDateTime now);

    @Query(value = """
            select close_price_id, stock_id, price_date, available_at, close_price, created_date, updated_date
            from (
                select d.*, row_number() over (partition by d.stock_id order by d.price_date desc) as rn
                from daily_close_price d
                where d.stock_id in (:stockIds)
                  and d.available_at <= :now
            ) ranked
            where rn <= 2
            order by stock_id, price_date desc
            """, nativeQuery = true)
    List<DailyClosePrice> findLatestTwoByStockIds(
            @Param("stockIds") Collection<UUID> stockIds, @Param("now") LocalDateTime now);

    boolean existsByStockAndPriceDate(CompetitionStock stock, LocalDate priceDate);
}
