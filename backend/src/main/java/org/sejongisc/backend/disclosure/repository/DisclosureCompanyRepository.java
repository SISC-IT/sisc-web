package org.sejongisc.backend.disclosure.repository;

import java.util.Optional;
import java.util.UUID;
import org.sejongisc.backend.disclosure.entity.DisclosureCompany;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisclosureCompanyRepository extends JpaRepository<DisclosureCompany, UUID> {
  Optional<DisclosureCompany> findByCorpCode(String corpCode);
}
