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
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

@Entity
@Table(name = "disclosure_filing", uniqueConstraints =
    @UniqueConstraint(name = "uq_disclosure_filing_rcept_no", columnNames = "rcept_no"))
@Getter
@NoArgsConstructor
public class DisclosureFiling extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "filing_id", nullable = false, updatable = false)
  private UUID filingId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private DisclosureCompany company;

  @Column(name = "rcept_no", nullable = false, length = 14)
  private String receiptNo;

  @Column(name = "report_nm", nullable = false, columnDefinition = "text")
  private String title;

  @Column(name = "flr_nm", columnDefinition = "text")
  private String submitterName;

  @Column(name = "rcept_dt", nullable = false)
  private LocalDate receivedDate;

  @Column(name = "rm", columnDefinition = "text")
  private String remarks;

  @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
  private String rawPayload;

  @Column(name = "collected_at", nullable = false)
  private LocalDateTime collectedAt;

  public DisclosureFiling(DisclosureCompany company, String receiptNo, String title,
      String submitterName, LocalDate receivedDate, String remarks, String rawPayload,
      LocalDateTime collectedAt) {
    this.company = company;
    this.receiptNo = receiptNo;
    this.title = title;
    this.submitterName = submitterName;
    this.receivedDate = receivedDate;
    this.remarks = remarks;
    this.rawPayload = rawPayload;
    this.collectedAt = collectedAt;
  }
}
