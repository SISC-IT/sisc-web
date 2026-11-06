package org.sejongisc.backend.disclosure.repository;

import java.util.UUID;
import org.sejongisc.backend.disclosure.entity.DisclosureFiling;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DisclosureFilingRepository extends JpaRepository<DisclosureFiling, UUID> {
  boolean existsByReceiptNo(String receiptNo);
  Optional<DisclosureFiling> findByReceiptNo(String receiptNo);
}
