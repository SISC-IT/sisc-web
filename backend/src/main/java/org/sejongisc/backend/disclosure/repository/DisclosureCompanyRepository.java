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
  // DART 기업 고유번호 단건 조회
  Optional<DisclosureCompany> findByCorpCode(String corpCode);

  // 동시 수집 중복 방지용 비관적 락 조회
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select company from DisclosureCompany company where company.corpCode = :corpCode")
  Optional<DisclosureCompany> findForUpdate(@Param("corpCode") String corpCode);

  // 추적 대상 기업 목록 및 키워드(기업명·종목코드·고유번호) 검색
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
