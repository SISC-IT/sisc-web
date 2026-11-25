package org.sejongisc.backend.disclosure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.sejongisc.backend.disclosure.entity.DisclosureFiling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisclosureFilingRepository extends JpaRepository<DisclosureFiling, UUID> {
  // DART 공시 접수번호 단건 조회
  Optional<DisclosureFiling> findByReceiptNo(String receiptNo);

  // 기업별 정기보고서(보고서 코드 보유) 전체 목록 조회
  List<DisclosureFiling> findAllByCompany_CorpCodeAndReportCodeIsNotNull(String corpCode);

  // 기업별 최신 정기보고서 수집 시각 일괄 집계 (빨간 점 판별용)
  @Query("""
      select f.company.companyId as companyId,
             max(f.collectedAt) as latestCollectedAt
      from DisclosureFiling f
      where f.company.companyId in :companyIds
      group by f.company.companyId
      """)
  List<CompanyFilingSummary> summarizeLatestCollectedAtByCompanyIds(
      @Param("companyIds") List<UUID> companyIds);

  interface CompanyFilingSummary {
    UUID getCompanyId();
    LocalDateTime getLatestCollectedAt();
  }
}
