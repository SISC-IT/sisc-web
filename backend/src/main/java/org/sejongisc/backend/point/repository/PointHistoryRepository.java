package org.sejongisc.backend.point.repository;

import org.sejongisc.backend.point.entity.PointHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {
  // 포인트 기록이 하나도 없으므로 SUM(amount)는 NULL, COALESCE 덕분에 0 반환
  @Query("SELECT COALESCE(SUM(ph.amount), 0) FROM PointHistory ph WHERE ph.userId = :userId")
  int getCurrentBalance(@Param("userId") UUID userId);

  Page<PointHistory> findAllByUserId(UUID userId, Pageable pageable);

  void deleteAllByUserId(UUID userId);
}
