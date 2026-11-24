package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.AccountLedger;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AccountLedgerRepository extends JpaRepository<AccountLedger, UUID> {

    // 놓친 날 이자를 한 번에 지급하면 여러 행의 처리 시각이 같아서 이자 날짜로 한 번 더 정렬한다.
    Page<AccountLedger> findAllByAccountOrderByExecutedAtDescInterestDateDesc(
            CompetitionAccount account, Pageable pageable);

    // 놓친 밤의 예금을 거꾸로 계산할 때 쓴다. 그 밤이 지난 뒤의 입출금을 지금 예금에서 되돌린다.
    List<AccountLedger> findAllByAccountInAndExecutedAtGreaterThanEqual(
            Collection<CompetitionAccount> accounts, LocalDateTime from);

    // 그 시각 전에 예금에 더해진 이자 합계
    @Query("""
            select coalesce(sum(l.amount), 0) from AccountLedger l
            where l.account = :account and l.type = org.sejongisc.backend.competition.entity.LedgerType.INTEREST
              and l.executedAt < :before
            """)
    BigDecimal sumInterestExecutedBefore(@Param("account") CompetitionAccount account,
                                         @Param("before") LocalDateTime before);
}
