package org.sejongisc.backend.disclosure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.sejongisc.backend.disclosure.entity.DisclosureUserRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisclosureUserReadRepository extends JpaRepository<DisclosureUserRead, UUID> {

  // 사용자별 추적 기업 목록의 마지막 확인 시각 일괄 조회 (빨간 점 판별용)
  List<DisclosureUserRead> findAllByUserIdAndCompany_CompanyIdIn(UUID userId, List<UUID> companyIds);

  // 사용자·기업별 마지막 확인 시각 원자적 Upsert (동시 더블클릭 충돌 방어)
  @Modifying
  @Query(value = """
      insert into disclosure_user_read (read_id, user_id, company_id, last_viewed_at, created_date, updated_date)
      values (:readId, :userId, :companyId, :viewedAt, :viewedAt, :viewedAt)
      on conflict (user_id, company_id)
      do update set last_viewed_at = excluded.last_viewed_at,
                    updated_date = excluded.updated_date
      """, nativeQuery = true)
  void upsertLastViewedAt(
      @Param("readId") UUID readId,
      @Param("userId") UUID userId,
      @Param("companyId") UUID companyId,
      @Param("viewedAt") LocalDateTime viewedAt);
}
