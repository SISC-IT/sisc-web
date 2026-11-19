package org.sejongisc.backend.disclosure.repository;

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
}
