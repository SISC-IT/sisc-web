package org.sejongisc.backend.point.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.point.dto.AccountEntry;
import org.sejongisc.backend.point.entity.Account;
import org.sejongisc.backend.point.entity.AccountName;
import org.sejongisc.backend.point.entity.AccountType;
import org.sejongisc.backend.point.entity.TransactionReason;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointDataInitializer implements CommandLineRunner {
  private final AccountService accountService;
  private final PointLedgerService pointLedgerService;
  private final UserService userService;
  private final TransactionTemplate transactionTemplate;

  /**
   * 복식부기 기반 포인트 시스템에 필요한 계정 초기화
   * - 시스템 계정 존재 여부 확인 후 생성
   * - 사용자 계정 존재 여부 확인 후 생성
   * - 사용자의 기존 포인트 마이그레이션 트랜잭션 (시스템 -> 사용자 계정)
   * TODO: 해당 초기화 로직은 flyway 도입 시 삭제 가능
   */
  @Override
  public void run(String... args) {
    log.info("=== 포인트 시스템 계정 초기화 및 데이터 마이그레이션 시작 ===");

    // 시스템 계정 초기화
    accountService.initSystemAccount(AccountName.SYSTEM_ISSUANCE, AccountType.SYSTEM);
    accountService.initSystemAccount(AccountName.BETTING_POOL, AccountType.PLATFORM);
    // TODO: 기능 추가 시 계정 초기화도 추가 필요

    // 사용자 계정 생성 + 포인트 마이그레이션
    transactionTemplate.execute(status -> {
      migrateExistingUsers();
      return null;
    });

    log.info("=== 포인트 시스템 초기화 완료 ===");
  }

  /**
   * 사용자 계정 생성 + 포인트 마이그레이션
   */
  public void migrateExistingUsers() {
    // 포인트 계정이 없는 사용자 리스트 조회
    List<User> users = userService.findAllUsersMissingAccount();

    // 시스템 계정
    Account systemAccount = accountService.getAccountByName(AccountName.SYSTEM_ISSUANCE);

    for (User user : users) {
      try {
        // 계정 생성
        Account userAccount = accountService.createUserAccount(user.getUserId());

        // 기존 포인트 마이그레이션
        long point = user.getPoint();
        if (point > 0) {
          pointLedgerService.processTransaction(
            TransactionReason.MIGRATION,
            user.getUserId(),
            AccountEntry.credit(systemAccount, point),
            AccountEntry.debit(userAccount, point)
          );
          log.info("마이그레이션 완료: User={}, Point={}", user.getEmail(), point);
        }
      } catch (Exception e) {
        log.error("유저 마이그레이션 실패: {}", user.getEmail(), e);
      }
    }
  }

}
