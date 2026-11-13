package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompetitionAccountRepository extends JpaRepository<CompetitionAccount, UUID> {

    Optional<CompetitionAccount> findByParticipant(CompetitionParticipant participant);
}
