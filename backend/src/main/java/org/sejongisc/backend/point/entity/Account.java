package org.sejongisc.backend.point.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID accountId;

  private UUID ownerId;

  @Enumerated(EnumType.STRING)
  @Column(columnDefinition = "VARCHAR(255)", nullable = false)
  private AccountName accountName;

  @Enumerated(EnumType.STRING)
  @Column(columnDefinition = "VARCHAR(255)", nullable = false)
  private AccountType type;

  @Column(nullable = false)
  private long balance;

  @Version
  private Long version;

  public void updateBalance(Long amount) {
    if (amount == null) {
      throw new CustomException(ErrorCode.INVALID_POINT_AMOUNT);
    }
    this.balance += amount;
  }
}
