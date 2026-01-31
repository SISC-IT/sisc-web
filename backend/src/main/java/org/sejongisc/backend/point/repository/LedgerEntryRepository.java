package org.sejongisc.backend.point.repository;

import org.sejongisc.backend.point.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
  /**
   * 사용자의 계좌 기반으로 전체 원장 내역 최신순 조회
   * PointTransaction을 fetch join으로 함께 조회
   */
  @Query(
    "SELECT le FROM LedgerEntry le " +
    "JOIN FETCH le.transaction " +
    "WHERE le.account.ownerId = :ownerId " +
    "ORDER BY le.createdDate DESC")
  Page<LedgerEntry> findAllByOwnerId(@Param("ownerId") UUID ownerId, Pageable pageable);
}
