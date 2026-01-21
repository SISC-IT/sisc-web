package org.sejongisc.backend.betting.repository;

import org.sejongisc.backend.betting.entity.BetRound;
import org.sejongisc.backend.betting.entity.UserBet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBetRepository extends JpaRepository<UserBet, UUID> {
    Optional<UserBet> findByRoundAndUserId(BetRound round, UUID userId);

    @Query(
        "SELECT ub FROM UserBet ub " +
        "JOIN FETCH ub.round " +
        "WHERE ub.userBetId = :userBetId " +
        "AND ub.userId = :userId")
    Optional<UserBet> findByUserBetIdAndUserIdWithRound(@Param("userBetId") UUID userBetId, @Param("userId") UUID userId);

    List<UserBet> findAllByUserIdOrderByRound_SettleAtDesc(UUID userId);

    List<UserBet> findAllByRoundIn(List<BetRound> rounds);
}
