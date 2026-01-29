package org.sejongisc.backend.point.dto;

import org.sejongisc.backend.point.entity.LedgerEntry;
import org.sejongisc.backend.point.entity.TransactionReason;

import java.time.LocalDateTime;
import java.util.UUID;

public record PointHistoryItem(
  UUID entryId,
  TransactionReason reason,
  Long amount,
  LocalDateTime createdDate
) {
  /**
   * Entity -> DTO 정적 팩토리 메서드
   */
  public static PointHistoryItem from(LedgerEntry entry) {
    return new PointHistoryItem(
      entry.getEntryId(),
      entry.getTransaction().getReason(),
      entry.getAmount(),
      entry.getCreatedDate()
    );
  }
}