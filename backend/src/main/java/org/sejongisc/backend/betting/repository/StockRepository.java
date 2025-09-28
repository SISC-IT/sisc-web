package org.sejongisc.backend.betting.repository;

import org.sejongisc.backend.betting.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {
}
