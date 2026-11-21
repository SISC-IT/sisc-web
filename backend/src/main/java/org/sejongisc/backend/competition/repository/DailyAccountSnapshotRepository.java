package org.sejongisc.backend.competition.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.DailyAccountSnapshot;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyAccountSnapshotRepository extends JpaRepository<DailyAccountSnapshot, UUID> {

    @Query("""
            select count(s) > 0 from DailyAccountSnapshot s
            where s.account.participant.competition = :competition
              and s.snapshotDate = :snapshotDate
            """)
    boolean existsByCompetitionAndSnapshotDate(
            @Param("competition") Competition competition, @Param("snapshotDate") LocalDate snapshotDate);

    @Query("""
            select max(s.snapshotDate) from DailyAccountSnapshot s
            where s.account.participant.competition = :competition
            """)
    Optional<LocalDate> findLatestSnapshotDate(@Param("competition") Competition competition);

    @Query("""
            select max(s.snapshotDate) from DailyAccountSnapshot s
            where s.account.participant.competition = :competition
              and s.snapshotDate < :date
            """)
    Optional<LocalDate> findLatestSnapshotDateBefore(
            @Param("competition") Competition competition, @Param("date") LocalDate date);

    @Query("""
            select s from DailyAccountSnapshot s
            join fetch s.account a
            join fetch a.participant p
            where p.competition = :competition
              and s.snapshotDate = :snapshotDate
              and p.status = :status
            order by s.rank
            """)
    List<DailyAccountSnapshot> findAllByCompetitionAndSnapshotDate(
            @Param("competition") Competition competition,
            @Param("snapshotDate") LocalDate snapshotDate,
            @Param("status") ParticipantStatus status);
}
