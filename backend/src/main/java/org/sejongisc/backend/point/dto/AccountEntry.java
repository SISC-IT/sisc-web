package org.sejongisc.backend.point.dto;

import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.point.entity.Account;
import org.sejongisc.backend.point.entity.EntryType;

public record AccountEntry(
  Account account,
  Long amount,
  EntryType entryType
) {
  public AccountEntry {
    if (account == null) {
      throw new CustomException(ErrorCode.ACCOUNT_REQUIRED);
    }
    if (amount == null || amount == 0) {
      throw new CustomException(ErrorCode.INVALID_POINT_AMOUNT);
    }
  }

  /**
   * 차변 항목 생성
   * 해당 계정에 잔액이 증가할 때 사용
   */
  public static AccountEntry debit(Account account, Long amount) {
    return new AccountEntry(account, Math.abs(amount), EntryType.DEBIT);
  }

  /**
   * 대변 항목 생성
   * 해당 계정에 잔액이 감소할 때 사용
   */
  public static AccountEntry credit(Account account, Long amount) {
    return new AccountEntry(account, -Math.abs(amount), EntryType.CREDIT);
  }
}
