package org.sejongisc.backend.stock.repository;

import org.sejongisc.backend.stock.entity.PriceData;
import org.sejongisc.backend.stock.entity.PriceDataId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceDataRepository extends JpaRepository<PriceData, PriceDataId> {
    List<PriceData> findByTickerAndDateBetweenOrderByDateAsc(String ticker, LocalDate startDate, LocalDate endDate);
    List<PriceData> findByTicker(String ticker);
    Optional<PriceData> findTopByTickerOrderByDateDesc(String ticker);
    /**
     * PriceData 테이블에 존재하는 모든 유니크한 티커(ticker) 목록을 조회합니다.
     */
    @Query("SELECT DISTINCT p.ticker FROM PriceData p")
    List<String> findDistinctTickers();

    // 모든 티커 중 중복을 제거하고 랜덤으로 하나만 가져옵니다.
    @Query(value = "SELECT DISTINCT ticker FROM price_data ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<String> findRandomTicker();
}