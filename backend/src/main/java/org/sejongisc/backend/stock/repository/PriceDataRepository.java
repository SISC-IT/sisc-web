package org.sejongisc.backend.stock.repository;

import org.sejongisc.backend.stock.entity.PriceData;
import org.sejongisc.backend.stock.entity.PriceDataId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PriceDataRepository extends JpaRepository<PriceData, PriceDataId> {
    List<PriceData> findByTickerAndDateBetweenOrderByDateAsc(String ticker, LocalDate startDate, LocalDate endDate);
    List<PriceData> findByTicker(String ticker);
}