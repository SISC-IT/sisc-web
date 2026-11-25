package org.sejongisc.backend.disclosure.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.sejongisc.backend.disclosure.entity.DisclosureFinancialMetric;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisclosureFinancialMetricRepository extends JpaRepository<DisclosureFinancialMetric, UUID> {
  // 동일 보고서·지표·연결구분·기간·계정 중복 저장 여부 확인
  boolean existsByFiling_FilingIdAndMetricTypeAndBasisAndPeriodTypeAndAccountId(
      UUID filingId, String metricType, String basis, String periodType, String accountId);

  // 보고서 ID 목록 기준 매출·영업이익·순이익 지표 일괄 조회
  List<DisclosureFinancialMetric> findAllByFiling_FilingIdInAndMetricTypeIn(
      Collection<UUID> filingIds, Collection<String> metricTypes);
}
