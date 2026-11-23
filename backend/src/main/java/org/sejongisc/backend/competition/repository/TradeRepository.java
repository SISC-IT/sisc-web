package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    Optional<Trade> findByAccountAndIdempotencyKey(CompetitionAccount account, UUID idempotencyKey);

    // 내역 화면에 종목명이 필요해서 종목을 함께 읽는다(N+1 방지).
    @EntityGraph(attributePaths = "stock")
    Page<Trade> findAllByAccountOrderByExecutedAtDesc(CompetitionAccount account, Pageable pageable);
}
