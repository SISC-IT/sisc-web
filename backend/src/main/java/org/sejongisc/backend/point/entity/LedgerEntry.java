package org.sejongisc.backend.point.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID entryId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_id", nullable = false)
  private PointTransaction transaction;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @Column(nullable = false)
  private Long amount;

  @Enumerated(EnumType.STRING)
  @Column(columnDefinition = "VARCHAR(255)", nullable = false)
  private EntryType entryType;
}
