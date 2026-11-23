package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetitionRepository extends JpaRepository<Competition, UUID> {

    // READY·ACTIVE는 부분 유니크 인덱스로 통틀어 최대 1개. 다른 상태는 여러 개일 수 있어 시작일이 가장 늦은 것을 가져온다.
    Optional<Competition> findFirstByStatusOrderByStartDateDesc(CompetitionStatus status);

    List<Competition> findAllByStatusAndStartDateLessThanEqual(CompetitionStatus status, LocalDate date);

    // 이 컬럼만 바꾼다. 엔티티로 저장하면 행 전체를 덮어써서, 그 사이 바뀐 대회 상태를 되돌릴 수 있다.
    // 같은 트랜잭션에서 고친 계좌·원장이 먼저 DB에 반영되도록 flush한다.
    @Modifying(flushAutomatically = true)
    @Query("update Competition c set c.interestPaidDate = :date where c.competitionId = :competitionId")
    void updateInterestPaidDate(@Param("competitionId") UUID competitionId, @Param("date") LocalDate date);
}
