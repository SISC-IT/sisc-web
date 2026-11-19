package org.sejongisc.backend.disclosure.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.sejongisc.backend.disclosure.entity.DisclosureFiling;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisclosureFilingRepository extends JpaRepository<DisclosureFiling, UUID> {
  boolean existsByReceiptNo(String receiptNo);
  Optional<DisclosureFiling> findByReceiptNo(String receiptNo);
  List<DisclosureFiling> findAllByCompany_CorpCodeAndReportCodeIsNotNull(String corpCode);
}
