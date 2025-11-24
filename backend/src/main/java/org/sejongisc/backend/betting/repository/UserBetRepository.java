package org.sejongisc.backend.betting.repository;

import org.sejongisc.backend.betting.entity.BetRound;
import org.sejongisc.backend.betting.entity.BetStatus;
import org.sejongisc.backend.betting.entity.UserBet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBetRepository extends JpaRepository<UserBet, UUID> {
    boolean existsByRoundAndUserId(BetRound round, UUID userId);

    Optional<UserBet> findByUserBetIdAndUserId(UUID userBetId, UUID userId);

    List<UserBet> findAllByUserIdOrderByRound_SettleAtDesc(UUID userId);

    List<UserBet> findAllByRound(BetRound round);


}
