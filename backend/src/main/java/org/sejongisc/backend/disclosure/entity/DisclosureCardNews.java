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

@Entity
@Table(name = "disclosure_card_news", uniqueConstraints =
    @UniqueConstraint(
        name = "uq_disclosure_card_news_rcept_category",
        columnNames = {"dart_rcept_no", "category"}))
@Getter
@NoArgsConstructor
public class DisclosureCardNews extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "card_id", nullable = false, updatable = false)
  private UUID cardId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private DisclosureCompany company;

  @Column(name = "dart_rcept_no", nullable = false, length = 14)
  private String receiptNo;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 40)
  private DisclosureCardCategory category;

  @Column(name = "report_title", nullable = false, columnDefinition = "text")
  private String reportTitle;

  @Column(name = "headline", nullable = false, columnDefinition = "text")
  private String headline;

  @Column(name = "published_date", nullable = false)
  private LocalDate publishedDate;

  @Column(name = "market_cls", length = 1)
  private String marketClass;

  @Column(name = "metrics_json", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String metricsJson;

  @Column(name = "tags_json", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String tagsJson;

  @Column(name = "dart_url", nullable = false, columnDefinition = "text")
  private String dartUrl;

  @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String rawPayload;

  @Column(name = "collected_at", nullable = false)
  private LocalDateTime collectedAt;

  public DisclosureCardNews(DisclosureCompany company, String receiptNo,
      DisclosureCardCategory category, String reportTitle, String headline,
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
