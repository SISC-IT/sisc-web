package org.sejongisc.backend.disclosure.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisclosureCompanyRepository extends JpaRepository<DisclosureCompany, UUID> {
  Optional<DisclosureCompany> findByCorpCode(String corpCode);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select company from DisclosureCompany company where company.corpCode = :corpCode")
  Optional<DisclosureCompany> findForUpdate(@Param("corpCode") String corpCode);

  @Query("""
      select c from DisclosureCompany c
      where c.tracked = true
        and (:keyword is null
             or lower(c.companyName) like lower(concat('%', :keyword, '%'))
             or lower(coalesce(c.stockCode, '')) like lower(concat('%', :keyword, '%'))
             or c.corpCode like concat('%', :keyword, '%'))
      order by c.companyName asc
      """)
  Page<DisclosureCompany> findTrackedCompanies(
      @Param("keyword") String keyword,
      Pageable pageable);
}
