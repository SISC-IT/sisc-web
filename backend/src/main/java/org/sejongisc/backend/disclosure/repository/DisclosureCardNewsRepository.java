package org.sejongisc.backend.disclosure.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.sejongisc.backend.disclosure.entity.DisclosureCardCategory;
import org.sejongisc.backend.disclosure.entity.DisclosureCardNews;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisclosureCardNewsRepository extends JpaRepository<DisclosureCardNews, UUID> {

  Optional<DisclosureCardNews> findByReceiptNoAndCategory(String receiptNo,
      DisclosureCardCategory category);

  @EntityGraph(attributePaths = "company")
  Optional<DisclosureCardNews> findWithCompanyByCardId(UUID cardId);

  @EntityGraph(attributePaths = "company")
  List<DisclosureCardNews> findAllByCompany_CompanyIdOrderByPublishedDateDescCreatedDateDesc(
      UUID companyId);

  @EntityGraph(attributePaths = "company")
  List<DisclosureCardNews> findAllByCompany_CompanyIdAndCategoryOrderByPublishedDateDescCreatedDateDesc(
      UUID companyId, DisclosureCardCategory category);

  @Query("""
      select c.company.companyId as companyId,
             max(c.collectedAt) as latestCollectedAt,
             max(c.publishedDate) as latestPublishedDate,
             count(c) as eventCount
      from DisclosureCardNews c
      where c.company.companyId in :companyIds
      group by c.company.companyId
      """)
  List<CompanyEventSummary> summarizeByCompanyIds(@Param("companyIds") List<UUID> companyIds);

  @EntityGraph(attributePaths = "company")
  @Query("""
      select c from DisclosureCardNews c
      join c.company comp
      where (:category is null or c.category = :category)
        and (:keyword is null
             or lower(comp.companyName) like lower(concat('%', :keyword, '%'))
             or lower(coalesce(comp.stockCode, '')) like lower(concat('%', :keyword, '%'))
             or lower(c.headline) like lower(concat('%', :keyword, '%'))
             or lower(c.reportTitle) like lower(concat('%', :keyword, '%')))
      """)
  Page<DisclosureCardNews> search(
      @Param("category") DisclosureCardCategory category,
      @Param("keyword") String keyword,
      Pageable pageable);

  interface CompanyEventSummary {
    UUID getCompanyId();
    LocalDateTime getLatestCollectedAt();
    LocalDate getLatestPublishedDate();
    long getEventCount();
  }
}
