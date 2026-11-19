package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetitionStockRepository extends JpaRepository<CompetitionStock, UUID> {

    Optional<CompetitionStock> findByTicker(String ticker);

    List<CompetitionStock> findByMarket(Market market);
}
