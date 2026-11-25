package org.sejongisc.backend.disclosure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

// 4대 이벤트 공시(수주·주주환원·CAPEX·잠정실적) 엔티티
@Entity
@Table(name = "disclosure_event", uniqueConstraints =
    @UniqueConstraint(
        name = "uq_disclosure_event_receipt_category",
        columnNames = {"receipt_no", "category"}))
@Getter
@NoArgsConstructor
public class DisclosureEvent extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID eventId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private DisclosureCompany company;

  @Column(nullable = false, length = 14)
  private String receiptNo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private DisclosureEventCategory category;

  @Column(nullable = false, columnDefinition = "text")
  private String reportTitle;

  @Column(nullable = false, columnDefinition = "text")
  private String headline;

  @Column(nullable = false)
  private LocalDate publishedDate;

  @Column(length = 1)
  private String marketClass;

  @Column(nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String metricsJson;

  @Column(nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String tagsJson;

  @Column(nullable = false, columnDefinition = "text")
  private String dartUrl;

  @Column(nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String rawPayload;

  @Column(nullable = false)
  private LocalDateTime collectedAt;

  public DisclosureEvent(DisclosureCompany company, String receiptNo,
      DisclosureEventCategory category, String reportTitle, String headline,
      LocalDate publishedDate, String marketClass, String metricsJson, String tagsJson,
      String dartUrl, String rawPayload, LocalDateTime collectedAt) {
    this.company = company;
    this.receiptNo = receiptNo;
    this.category = category;
    this.reportTitle = reportTitle;
    this.headline = headline;
    this.publishedDate = publishedDate;
    this.marketClass = marketClass;
    this.metricsJson = metricsJson;
    this.tagsJson = tagsJson;
    this.dartUrl = dartUrl;
    this.rawPayload = rawPayload;
    this.collectedAt = collectedAt;
  }

  public void refresh(String reportTitle, String headline, LocalDate publishedDate,
      String marketClass, String metricsJson, String tagsJson, String rawPayload,
      LocalDateTime collectedAt) {
    this.reportTitle = reportTitle;
    this.headline = headline;
    this.publishedDate = publishedDate;
    this.marketClass = marketClass;
    this.metricsJson = metricsJson;
    this.tagsJson = tagsJson;
    this.rawPayload = rawPayload;
    this.collectedAt = collectedAt;
  }
}
