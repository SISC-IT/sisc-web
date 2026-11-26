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
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

@Entity
@Table(name = "disclosure_user_read", uniqueConstraints =
    @UniqueConstraint(name = "uq_disclosure_user_read", columnNames = {"user_id", "company_id"}))
@Getter
@NoArgsConstructor
public class DisclosureUserRead extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID readId;

  @Column(nullable = false)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private DisclosureCompany company;

  @Column(nullable = false)
  private LocalDateTime lastViewedAt;

  public DisclosureUserRead(UUID userId, DisclosureCompany company, LocalDateTime lastViewedAt) {
    this.userId = userId;
    this.company = company;
    this.lastViewedAt = lastViewedAt;
  }

  public void markViewed(LocalDateTime viewedAt) {
    this.lastViewedAt = viewedAt;
  }
}
