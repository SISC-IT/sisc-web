package org.sejongisc.backend.disclosure.repository;

import java.util.UUID;
import org.sejongisc.backend.disclosure.entity.DisclosureFinancialMetric;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisclosureFinancialMetricRepository extends JpaRepository<DisclosureFinancialMetric, UUID> {
  boolean existsByFiling_FilingIdAndMetricTypeAndBasisAndPeriodTypeAndAccountId(
      UUID filingId, String metricType, String basis, String periodType, String accountId);
}
