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
  private PointTransaction transaction;

  @ManyToOne(fetch = FetchType.LAZY)
  private Account account;

  private Long amount;

  @Enumerated(EnumType.STRING)
  @Column(columnDefinition = "VARCHAR(255)")
  private EntryType entryType;
}
