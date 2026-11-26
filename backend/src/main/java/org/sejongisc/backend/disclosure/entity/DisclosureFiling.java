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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

@Entity
@Table(name = "disclosure_filing", uniqueConstraints =
    @UniqueConstraint(name = "uq_disclosure_filing_receipt_no", columnNames = "receipt_no"))
@Getter
@NoArgsConstructor
public class DisclosureFiling extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID filingId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private DisclosureCompany company;

  @Column(nullable = false, length = 14)
  private String receiptNo;

  @Column(nullable = false, columnDefinition = "text")
  private String title;

  @Column(columnDefinition = "text")
  private String submitterName;

  @Column(nullable = false)
  private LocalDate receivedDate;

  private Integer businessYear;

  @Column(length = 5)
  private String reportCode;

  @Column(columnDefinition = "text")
  private String remarks;

  @Column(nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String rawPayload;

  @Column(nullable = false)
  private LocalDateTime collectedAt;

  public void setFinancialReport(int businessYear, String reportCode) {
    this.businessYear = businessYear;
    this.reportCode = reportCode;
  }

  public DisclosureFiling(DisclosureCompany company, String receiptNo, String title,
      String submitterName, LocalDate receivedDate, Integer businessYear, String reportCode,
      String remarks, String rawPayload, LocalDateTime collectedAt) {
    this.company = company;
    this.receiptNo = receiptNo;
    this.title = title;
    this.submitterName = submitterName;
    this.receivedDate = receivedDate;
    this.businessYear = businessYear;
    this.reportCode = reportCode;
    this.remarks = remarks;
    this.rawPayload = rawPayload;
    this.collectedAt = collectedAt;
  }
}
