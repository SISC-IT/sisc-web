package org.sejongisc.backend.point.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.point.dto.AccountEntry;
import org.sejongisc.backend.point.entity.LedgerEntry;
import org.sejongisc.backend.point.entity.PointTransaction;
import org.sejongisc.backend.point.entity.TransactionReason;
import org.sejongisc.backend.point.repository.LedgerEntryRepository;
import org.sejongisc.backend.point.repository.TransactionalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PointLedgerService {
  private final TransactionalRepository transactionalRepository;
  private final LedgerEntryRepository ledgerEntryRepository;

  /**
   * 포인트 거래를 처리하고 원장에 기록
   *
   * @param reason  거래 발생 사유 (예: 베팅, 출석체크 등)
   * @param refId   외부 도메인의 참조 ID
   * @param entries 거래에 참여하는 계정 & 각 계정별 증감 금액
   *                - 가변 인자로, 정해진 개수 없이 추가 가능합니다.
   */
  @Transactional
  public void processTransaction(TransactionReason reason, UUID refId, AccountEntry... entries) {
    List<AccountEntry> entryList = Arrays.asList(entries);

    // 분개 항목의 amount의 합이 0인지 검증
    long sum = entryList.stream().mapToLong(AccountEntry::amount).sum();
    if (sum != 0) {
      throw new CustomException(ErrorCode.POINT_TRANSACTION_TOTAL_MISMATCH);
    }

    // 트랜잭션 생성
    PointTransaction transaction = transactionalRepository.save(
      PointTransaction.builder()
        .reason(reason)
        .refId(refId)
        .build()
    );

    for (AccountEntry entry : entryList) {
      entry.account().updateBalance(entry.amount());
      // 분개 생성
      ledgerEntryRepository.save(LedgerEntry.builder()
        .transaction(transaction)
        .account(entry.account())
        .amount(entry.amount())
        .entryType(entry.entryType())
        .build()
      );
    }
  }
}
