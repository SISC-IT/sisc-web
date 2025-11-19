package org.sejongisc.backend.betting.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.sejongisc.backend.betting.entity.BetRound;
import org.sejongisc.backend.betting.entity.Scope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BetRoundRepository extends JpaRepository<BetRound, UUID> {
    Optional<BetRound> findTopByStatusTrueAndScopeOrderByOpenAtDesc(Scope type);

    List<BetRound> findAllByOrderBySettleAtDesc();

    List<BetRound> findByStatusTrueAndLockAtLessThanEqual(LocalDateTime now);

    List<BetRound> findByStatusFalseAndSettleAtIsNullAndLockAtLessThanEqual(LocalDateTime now);

    // [추가] 상승(UP) 통계 원자적 업데이트
    @Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트 초기화 (데이터 동기화)
    @Query("UPDATE BetRound b SET b.upBetCount = b.upBetCount + 1, b.upTotalPoints = b.upTotalPoints + :points WHERE b.betRoundID = :id")
    void incrementUpStats(@Param("id") UUID id, @Param("points") long points);

    // [추가] 하락(DOWN) 통계 원자적 업데이트
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BetRound b SET b.downBetCount = b.downBetCount + 1, b.downTotalPoints = b.downTotalPoints + :points WHERE b.betRoundID = :id")
    void incrementDownStats(@Param("id") UUID id, @Param("points") long points);
}
