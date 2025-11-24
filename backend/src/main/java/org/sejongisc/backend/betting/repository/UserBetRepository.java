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

    // JPQL을 사용하여 원자적 업데이트 수행
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserBet u SET u.betStatus = :newStatus WHERE u.userBetId = :id AND u.userId = :userId AND u.betStatus = :oldStatus")
    int updateStatusToCanceled(@Param("id") UUID id, @Param("userId") UUID userId, @Param("oldStatus") BetStatus oldStatus, @Param("newStatus") BetStatus newStatus);
}
