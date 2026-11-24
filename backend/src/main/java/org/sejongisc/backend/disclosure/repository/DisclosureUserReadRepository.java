package org.sejongisc.backend.disclosure.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.sejongisc.backend.disclosure.entity.DisclosureUserRead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisclosureUserReadRepository extends JpaRepository<DisclosureUserRead, UUID> {

  Optional<DisclosureUserRead> findByUserIdAndCompany_CompanyId(UUID userId, UUID companyId);

  List<DisclosureUserRead> findAllByUserIdAndCompany_CompanyIdIn(UUID userId, List<UUID> companyIds);
}
