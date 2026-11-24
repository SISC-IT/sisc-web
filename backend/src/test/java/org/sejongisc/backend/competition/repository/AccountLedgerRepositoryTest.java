package org.sejongisc.backend.competition.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import javax.sql.DataSource;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.competition.entity.AccountLedger;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.LedgerType;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(CompetitionJpaTestConfig.class)
@Transactional
class AccountLedgerRepositoryTest {

  private static final LocalDate MON = LocalDate.of(2026, 11, 23);

  @Autowired AccountLedgerRepository ledgerRepository;
  @Autowired CompetitionRepository competitionRepository;
  @Autowired CompetitionParticipantRepository participantRepository;
  @Autowired CompetitionAccountRepository accountRepository;
  @Autowired EntityManager entityManager;
  @Autowired DataSource dataSource;

  private Competition competition;

  @BeforeEach
  void setUp() {
    competition = competitionRepository.saveAndFlush(Competition.builder()
        .name("2026 2학기 수익률 대결")
        .status(CompetitionStatus.ACTIVE)
        .startDate(LocalDate.of(2026, 11, 16))
        .endDate(LocalDate.of(2026, 12, 11))
        .initialCash(new BigDecimal("10000000"))
        .feeRate(new BigDecimal("0.0001"))
        .depositInterestRate(new BigDecimal("0.03"))
        .build());
  }

  @Test
  void 여러_계좌의_기준_시각_이후_기록만_읽는다() {
    CompetitionAccount a = saveAccount();
    CompetitionAccount b = saveAccount();
    CompetitionAccount other = saveAccount();
    LocalDateTime from = LocalDateTime.of(2026, 11, 23, 0, 0);
    ledgerRepository.saveAndFlush(transfer(a, LedgerType.DEPOSIT, from.minusHours(7))); // 기준 전
    ledgerRepository.saveAndFlush(transfer(a, LedgerType.DEPOSIT, from.plusHours(17)));
    ledgerRepository.saveAndFlush(transfer(b, LedgerType.WITHDRAW, from)); // 기준 시각 포함
    ledgerRepository.saveAndFlush(transfer(other, LedgerType.DEPOSIT, from.plusHours(17))); // 다른 계좌
    entityManager.clear();

    assertThat(ledgerRepository.findAllByAccountInAndExecutedAtGreaterThanEqual(List.of(a, b), from))
        .extracting(AccountLedger::getExecutedAt)
        .containsExactlyInAnyOrder(from.plusHours(17), from);
  }

  @Test
  void 계좌의_내역을_최근순으로_읽고_처리_시각이_같으면_이자_날짜가_늦은_것이_먼저다() {
    CompetitionAccount account = saveAccount();
    ledgerRepository.saveAndFlush(transfer(account, LedgerType.DEPOSIT)); // 11/23 17:00
    // 놓친 날까지 한 번에 지급해 처리 시각이 같은 이자 두 건
    LocalDateTime batchTime = LocalDateTime.of(2026, 11, 24, 0, 0);
    ledgerRepository.saveAndFlush(interest(account, MON.minusDays(1), batchTime));
    ledgerRepository.saveAndFlush(interest(account, MON, batchTime));
    ledgerRepository.saveAndFlush(transfer(saveAccount(), LedgerType.DEPOSIT)); // 다른 계좌
    entityManager.clear();

    Page<AccountLedger> page =
        ledgerRepository.findAllByAccountOrderByExecutedAtDescInterestDateDesc(account, PageRequest.of(0, 20));

    assertThat(page.getTotalElements()).isEqualTo(3);
    assertThat(page.getContent()).extracting(AccountLedger::getType)
        .containsExactly(LedgerType.INTEREST, LedgerType.INTEREST, LedgerType.DEPOSIT);
    assertThat(page.getContent()).extracting(AccountLedger::getInterestDate)
        .containsExactly(MON, MON.minusDays(1), null);
  }

  @Test
  void 이자_지급일만_바꾸고_그_사이_바뀐_대회_상태는_되돌리지_않는다() {
    // 배치가 대회를 읽은 뒤 다른 작업이 대회를 종료했다고 가정한다
    new JdbcTemplate(dataSource).update(
        "update competition set status = 'ENDED' where competition_id = ?", competition.getCompetitionId());

    competitionRepository.updateInterestPaidDate(competition.getCompetitionId(), MON);
    entityManager.clear();

    Competition reloaded = competitionRepository.findById(competition.getCompetitionId()).orElseThrow();
    assertThat(reloaded.getInterestPaidDate()).isEqualTo(MON);
    assertThat(reloaded.getStatus()).isEqualTo(CompetitionStatus.ENDED);
  }

  @Test
  void 그날_전날_밤까지의_이자를_이자_날짜로_합한다() {
    CompetitionAccount account = saveAccount();
    ledgerRepository.saveAndFlush(interest(account, MON.minusDays(1)));
    // 월요일 밤 이자를 늦게(수요일에) 지급해도 월요일 밤 몫으로 센다
    ledgerRepository.saveAndFlush(interest(account, MON, MON.plusDays(2).atTime(15, 0)));
    ledgerRepository.saveAndFlush(transfer(account, LedgerType.DEPOSIT)); // 입금은 이자가 아니다

    assertThat(ledgerRepository.sumInterestForNightsBefore(account, MON)).isEqualByComparingTo("82.1917");
    assertThat(ledgerRepository.sumInterestForNightsBefore(account, MON.plusDays(1))).isEqualByComparingTo("164.3834");
    assertThat(ledgerRepository.sumInterestForNightsBefore(saveAccount(), MON.plusDays(1))).isEqualByComparingTo("0");
  }

  @Test
  void 같은_계좌_같은_날짜_이자는_두_번_저장할_수_없다() {
    CompetitionAccount account = saveAccount();
    ledgerRepository.saveAndFlush(interest(account, MON));

    assertThatThrownBy(() -> ledgerRepository.saveAndFlush(interest(account, MON)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .cause()
        .isInstanceOf(ConstraintViolationException.class)
        .extracting("constraintName").isEqualTo("uk_account_ledger_account_interest_date");
  }

  @Test
  void 입출금은_같은_계좌에서_여러_번_저장할_수_있다() {
    CompetitionAccount account = saveAccount();
    ledgerRepository.saveAndFlush(transfer(account, LedgerType.DEPOSIT));
    ledgerRepository.saveAndFlush(transfer(account, LedgerType.DEPOSIT));
    ledgerRepository.saveAndFlush(transfer(account, LedgerType.WITHDRAW));

    assertThat(ledgerRepository.count()).isEqualTo(3);
  }

  @Test
  void 이자는_날짜가_있어야_한다() {
    AccountLedger interestWithoutDate = AccountLedger.builder()
        .account(saveAccount()).type(LedgerType.INTEREST).amount(new BigDecimal("82"))
        .cashAfter(BigDecimal.ZERO).depositAfter(new BigDecimal("1000082"))
        .executedAt(MON.atStartOfDay())
        .build();

    assertThatThrownBy(() -> ledgerRepository.saveAndFlush(interestWithoutDate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 입출금은_날짜가_없어야_한다() {
    AccountLedger depositWithDate = AccountLedger.builder()
        .account(saveAccount()).type(LedgerType.DEPOSIT).amount(new BigDecimal("1000000"))
        .cashAfter(new BigDecimal("9000000")).depositAfter(new BigDecimal("1000000"))
        .interestDate(MON).executedAt(MON.atTime(17, 0))
        .build();

    assertThatThrownBy(() -> ledgerRepository.saveAndFlush(depositWithDate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 금액은_0이_될_수_없다() {
    AccountLedger zero = AccountLedger.builder()
        .account(saveAccount()).type(LedgerType.DEPOSIT).amount(BigDecimal.ZERO)
        .cashAfter(new BigDecimal("10000000")).depositAfter(BigDecimal.ZERO)
        .executedAt(MON.atTime(17, 0))
        .build();

    assertThatThrownBy(() -> ledgerRepository.saveAndFlush(zero))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private AccountLedger interest(CompetitionAccount account, LocalDate interestDate) {
    return interest(account, interestDate, interestDate.plusDays(1).atStartOfDay());
  }

  private AccountLedger interest(CompetitionAccount account, LocalDate interestDate, LocalDateTime executedAt) {
    return AccountLedger.builder()
        .account(account)
        .type(LedgerType.INTEREST)
        .amount(new BigDecimal("82.1917"))
        .cashAfter(new BigDecimal("9000000"))
        .depositAfter(new BigDecimal("1000082.1917"))
        .interestDate(interestDate)
        .executedAt(executedAt)
        .build();
  }

  private AccountLedger transfer(CompetitionAccount account, LedgerType type) {
    return transfer(account, type, LocalDateTime.of(2026, 11, 23, 17, 0));
  }

  private AccountLedger transfer(CompetitionAccount account, LedgerType type, LocalDateTime executedAt) {
    return AccountLedger.builder()
        .account(account)
        .type(type)
        .amount(new BigDecimal("1000000"))
        .cashAfter(new BigDecimal("9000000"))
        .depositAfter(new BigDecimal("1000000"))
        .executedAt(executedAt)
        .build();
  }

  private CompetitionAccount saveAccount() {
    UUID userId = UUID.randomUUID();
    new JdbcTemplate(dataSource).update("insert into users (user_id) values (?)", userId);
    CompetitionParticipant participant = participantRepository.saveAndFlush(CompetitionParticipant.builder()
        .competition(competition)
        .userId(userId)
        .teamName("매크로팀")
        .status(ParticipantStatus.ACTIVE)
        .build());
    return accountRepository.saveAndFlush(CompetitionAccount.builder()
        .participant(participant)
        .cash(new BigDecimal("10000000"))
        .deposit(BigDecimal.ZERO)
        .build());
  }
}
