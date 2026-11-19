package org.sejongisc.backend.disclosure.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisclosureCompanyRepository extends JpaRepository<DisclosureCompany, UUID> {
  Optional<DisclosureCompany> findByCorpCode(String corpCode);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select company from DisclosureCompany company where company.corpCode = :corpCode")
  Optional<DisclosureCompany> findForUpdate(@Param("corpCode") String corpCode);
}
