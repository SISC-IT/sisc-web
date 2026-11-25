package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.AccountLedger;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.LedgerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountLedgerRepository extends JpaRepository<AccountLedger, UUID> {

    // 놓친 날 이자를 한 번에 지급하면 여러 행의 처리 시각이 같아서 이자 날짜로 한 번 더 정렬한다.
    Page<AccountLedger> findAllByAccountOrderByExecutedAtDescInterestDateDesc(
            CompetitionAccount account, Pageable pageable);

    // 놓친 밤의 예금을 거꾸로 계산할 때 쓴다. 그 밤이 지난 뒤의 입출금을 지금 예금에서 되돌린다.
    List<AccountLedger> findAllByAccountInAndExecutedAtGreaterThanEqual(
            Collection<CompetitionAccount> accounts, LocalDateTime from);

    // 그날 전날 밤까지의 이자 합계. 이자는 자정에 지급되므로 그날 기준 예금에 들어 있어야 할 이자다.
    // 지급 시각이 아니라 이자 날짜로 세서, 늦게 지급된 이자도 원래 날짜에 포함한다.
    @Query("""
            select coalesce(sum(l.amount), 0) from AccountLedger l
            where l.account = :account and l.type = org.sejongisc.backend.competition.entity.LedgerType.INTEREST
              and l.interestDate < :day
            """)
    BigDecimal sumInterestForNightsBefore(@Param("account") CompetitionAccount account, @Param("day") LocalDate day);

    // 내 현황의 참여 판단에 쓴다. 입출금(DEPOSIT, WITHDRAW)만 넘겨 이자는 빼고 본다.
    boolean existsByAccountAndTypeInAndExecutedAtLessThan(
            CompetitionAccount account, Collection<LedgerType> types, LocalDateTime before);

    Optional<AccountLedger> findFirstByAccountAndTypeInAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtAsc(
            CompetitionAccount account, Collection<LedgerType> types, LocalDateTime from, LocalDateTime before);

    Optional<AccountLedger> findFirstByAccountAndTypeInAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtDesc(
            CompetitionAccount account, Collection<LedgerType> types, LocalDateTime from, LocalDateTime before);
}
