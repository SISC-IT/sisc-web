package org.sejongisc.backend.betting.repository;

import org.springframework.data.repository.query.Param;
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

    /**
     * 상승(UP) 통계 원자적 업데이트
     * - flushAutomatically: 해당 메서드 호출 전 변경사항을 DB에 flush -> 데이터 유실 방지
     * - clearAutomatically: 업데이트 후 1차 캐시를 비움 -> 조회 시 데이터 정합성 문제 방지
     *      -> 이후 필요하다면 DB의 최신값 조회 필요
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE BetRound b " +
        "SET b.upBetCount = b.upBetCount + 1, b.upTotalPoints = b.upTotalPoints + :points " +
        "WHERE b.betRoundID = :id")
    void incrementUpStats(@Param("id") UUID id, @Param("points") long points);

    /**
     * 하락(DOWN) 통계 원자적 업데이트
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE BetRound b " +
        "SET b.downBetCount = b.downBetCount + 1, b.downTotalPoints = b.downTotalPoints + :points " +
        "WHERE b.betRoundID = :id")
    void incrementDownStats(@Param("id") UUID id, @Param("points") long points);

    /**
     * 상승(UP) 통계 감소 (취소 시)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE BetRound b " +
        "SET b.upBetCount = b.upBetCount - 1, b.upTotalPoints = b.upTotalPoints - :points " +
        "WHERE b.betRoundID = :id")
    void decrementUpStats(@Param("id") UUID id, @Param("points") long points);

    /**
     * 하락(DOWN) 통계 감소 (취소 시)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE BetRound b " +
        "SET b.downBetCount = b.downBetCount - 1, b.downTotalPoints = b.downTotalPoints - :points " +
        "WHERE b.betRoundID = :id")
    void decrementDownStats(@Param("id") UUID id, @Param("points") long points);
}
