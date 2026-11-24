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
  Optional<DisclosureFiling> findByReceiptNo(String receiptNo);
  List<DisclosureFiling> findAllByCompany_CorpCodeAndReportCodeIsNotNull(String corpCode);

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
