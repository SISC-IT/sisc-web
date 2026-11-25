package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.Market;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetitionStockRepository extends JpaRepository<CompetitionStock, UUID> {

    Optional<CompetitionStock> findByTicker(String ticker);

    List<CompetitionStock> findByMarket(Market market);

    @Query("""
            select s from CompetitionStock s
            where (:market is null or s.market = :market)
              and (lower(s.name) like lower(concat('%', :keyword, '%'))
                or lower(s.ticker) like lower(concat('%', :keyword, '%')))
            """)
    Page<CompetitionStock> search(@Param("market") Market market, @Param("keyword") String keyword, Pageable pageable);
}
