package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HoldingRepository extends JpaRepository<Holding, UUID> {

    Optional<Holding> findByAccountAndStock(CompetitionAccount account, CompetitionStock stock);

    List<Holding> findAllByAccount(CompetitionAccount account);
}
