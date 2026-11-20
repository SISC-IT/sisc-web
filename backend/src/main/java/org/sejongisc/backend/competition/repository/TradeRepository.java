package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    Optional<Trade> findByAccountAndIdempotencyKey(CompetitionAccount account, UUID idempotencyKey);
}
