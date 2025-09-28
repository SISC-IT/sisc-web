package org.sejongisc.backend.betting.repository;

import org.sejongisc.backend.betting.entity.BetRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BetRoundRepository extends JpaRepository<BetRound, UUID> {
}
