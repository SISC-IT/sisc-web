package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetitionAccountRepository extends JpaRepository<CompetitionAccount, UUID> {

    Optional<CompetitionAccount> findByParticipant(CompetitionParticipant participant);

    @Query("""
            select a from CompetitionAccount a
            join fetch a.participant p
            where p.competition = :competition
            """)
    List<CompetitionAccount> findAllByCompetition(@Param("competition") Competition competition);

    @Query("""
            select a from CompetitionAccount a
            join fetch a.participant p
            where p.competition = :competition
              and p.status = :status
            """)
    List<CompetitionAccount> findAllByCompetitionAndParticipantStatus(
            @Param("competition") Competition competition, @Param("status") ParticipantStatus status);
}
