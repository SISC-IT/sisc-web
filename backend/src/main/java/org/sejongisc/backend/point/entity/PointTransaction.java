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
public class PointTransaction extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID pointTransactionId;

  @Enumerated(EnumType.STRING)
  @Column(columnDefinition = "VARCHAR(255)")
  private TransactionReason reason;

  private UUID refId;
}
