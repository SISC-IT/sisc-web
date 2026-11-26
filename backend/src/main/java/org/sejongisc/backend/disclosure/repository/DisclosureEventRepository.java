package org.sejongisc.backend.disclosure.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.sejongisc.backend.disclosure.entity.DisclosureEvent;
import org.sejongisc.backend.disclosure.entity.DisclosureEventCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisclosureEventRepository extends JpaRepository<DisclosureEvent, UUID> {

  // 접수번호·유형 기준 이벤트 공시 단건 조회 (중복 수집 방어용)
  Optional<DisclosureEvent> findByReceiptNoAndCategory(String receiptNo,
      DisclosureEventCategory category);

  // 특정 기업의 전체 이벤트 공시 최신순 조회
  @EntityGraph(attributePaths = "company")
  List<DisclosureEvent> findAllByCompany_CompanyIdOrderByPublishedDateDescCreatedDateDesc(
      UUID companyId);

  // 특정 기업의 선택 유형별 이벤트 공시 최신순 조회 (스프레드시트 필터용)
  @EntityGraph(attributePaths = "company")
  List<DisclosureEvent> findAllByCompany_CompanyIdAndCategoryOrderByPublishedDateDescCreatedDateDesc(
      UUID companyId, DisclosureEventCategory category);

  // 기업별 최신 이벤트 수집 시각·공시일·총 건수 일괄 집계 (목록 및 빨간 점용)
  @Query("""
      select e.company.companyId as companyId,
             max(e.collectedAt) as latestCollectedAt,
             max(e.publishedDate) as latestPublishedDate,
             count(e) as eventCount
      from DisclosureEvent e
      where e.company.companyId in :companyIds
      group by e.company.companyId
      """)
  List<CompanyEventSummary> summarizeByCompanyIds(@Param("companyIds") List<UUID> companyIds);

  interface CompanyEventSummary {
    UUID getCompanyId();
    LocalDateTime getLatestCollectedAt();
    LocalDate getLatestPublishedDate();
    long getEventCount();
  }
}
