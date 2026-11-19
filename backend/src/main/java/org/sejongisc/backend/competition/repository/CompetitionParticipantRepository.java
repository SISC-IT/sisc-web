package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetitionParticipantRepository extends JpaRepository<CompetitionParticipant, UUID> {

    Optional<CompetitionParticipant> findByCompetitionAndUserId(Competition competition, UUID userId);

    List<CompetitionParticipant> findAllByCompetition(Competition competition);
}
