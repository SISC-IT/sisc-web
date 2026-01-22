package org.sejongisc.backend.point.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.point.entity.Account;
import org.sejongisc.backend.point.entity.AccountName;
import org.sejongisc.backend.point.entity.AccountType;
import org.sejongisc.backend.point.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {
  private final AccountRepository accountRepository;

  /**
   * AccountName으로 계정 조회
   */
  @Transactional(readOnly = true)
  public Account getAccountByName(AccountName accountName) {
    return accountRepository.findByAccountName(accountName)
      .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));
  }

  /**
   * 사용자 계정 조회
   */
  @Transactional(readOnly = true)
  public Account getUserAccount(UUID userId) {
    return accountRepository.findByOwnerIdAndType(userId, AccountType.USER)
      .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));
  }

  /**
   * 사용자 계정 생성
   */
  @Transactional
  public Account createUserAccount(UUID userId) {
      return saveAccount(userId, AccountName.USER_ACCOUNT, AccountType.USER);
  }

  /**
   * 시스템 계정 초기화
   * 존재 여부 확인 후 없으면 계정 생성
   */
  @Transactional
  public void initSystemAccount(AccountName name, AccountType type) {
    if (!accountRepository.existsByAccountName(name)) {
      saveAccount(null, name, type);
    }
  }

  /**
   * Account 생성
   */
  private Account saveAccount(UUID ownerId, AccountName name, AccountType type) {
    return accountRepository.save(Account.builder()
      .ownerId(ownerId)
      .accountName(name)
      .type(type)
      .balance(0L)
      .build());
  }
}
