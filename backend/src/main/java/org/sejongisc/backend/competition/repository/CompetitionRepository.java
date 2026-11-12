package org.sejongisc.backend.competition.repository;

import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompetitionRepository extends JpaRepository<Competition, UUID> {

    // ACTIVE는 부분 유니크 인덱스로 최대 1개. 다른 상태는 여러 개일 수 있어 시작일이 가장 늦은 것을 가져온다.
    Optional<Competition> findFirstByStatusOrderByStartDateDesc(CompetitionStatus status);
}
