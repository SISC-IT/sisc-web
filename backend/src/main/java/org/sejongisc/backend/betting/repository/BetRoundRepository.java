package org.sejongisc.backend.betting.repository;

import org.sejongisc.backend.betting.entity.BetRound;
import org.sejongisc.backend.betting.entity.Scope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BetRoundRepository extends JpaRepository<BetRound, UUID> {
    Optional<BetRound> findByStatusTrueAndScope(Scope type);

    List<BetRound> findAllByOrderBySettleAtDesc();

    List<BetRound> findByStatusTrueAndLockAtLessThanEqual(LocalDateTime now);

    List<BetRound> findByStatusFalseAndSettleAtIsNullAndLockAtLessThanEqual(LocalDateTime now);
}
