package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    interface TradeActivity {
        UUID getAccountId();
        long getTradeCount();
        LocalDateTime getLastTradedAt();
    }

    // 거래가 없는 계좌는 결과에 없다.
    @Query("""
            select t.account.accountId as accountId, count(t) as tradeCount, max(t.executedAt) as lastTradedAt
            from Trade t
            where t.account.participant.competition = :competition
            group by t.account.accountId
            """)
    List<TradeActivity> findTradeActivities(@Param("competition") Competition competition);

    Optional<Trade> findByAccountAndIdempotencyKey(CompetitionAccount account, UUID idempotencyKey);

    // 내역 화면에 종목명이 필요해서 종목을 함께 읽는다(N+1 방지).
    @EntityGraph(attributePaths = "stock")
    Page<Trade> findAllByAccountOrderByExecutedAtDesc(CompetitionAccount account, Pageable pageable);

    @Query("select coalesce(sum(t.fee), 0) from Trade t where t.account = :account and t.executedAt < :before")
    BigDecimal sumFeeExecutedBefore(@Param("account") CompetitionAccount account,
                                    @Param("before") LocalDateTime before);

    boolean existsByAccountAndExecutedAtLessThan(CompetitionAccount account, LocalDateTime before);

    Optional<Trade> findFirstByAccountAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtAsc(
            CompetitionAccount account, LocalDateTime from, LocalDateTime before);

    Optional<Trade> findFirstByAccountAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtDesc(
            CompetitionAccount account, LocalDateTime from, LocalDateTime before);
}
