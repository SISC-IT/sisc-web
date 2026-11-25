package org.sejongisc.backend.disclosure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

@Entity
@Table(name = "disclosure_financial_metric")
@Getter
@NoArgsConstructor
public class DisclosureFinancialMetric extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID metricId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "filing_id", nullable = false)
  private DisclosureFiling filing;

  @Column(nullable = false, length = 40)
  private String metricType;

  @Column(length = 200)
  private String accountId;

  @Column(nullable = false, columnDefinition = "text")
  private String accountName;

  @Column(nullable = false, length = 3)
  private String basis;

  @Column(nullable = false, length = 20)
  private String periodType;

  @Column(precision = 30, scale = 4)
  private BigDecimal amount;

  @Column(length = 10)
  private String currency;

  @Column(nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String rawPayload;

  public DisclosureFinancialMetric(DisclosureFiling filing, String metricType, String accountId,
      String accountName, String basis, String periodType, BigDecimal amount, String currency,
      String rawPayload) {
    this.filing = filing;
    this.metricType = metricType;
    this.accountId = accountId;
    this.accountName = accountName;
    this.basis = basis;
    this.periodType = periodType;
    this.amount = amount;
    this.currency = currency;
    this.rawPayload = rawPayload;
  }
}
