package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.competition.entity.AccountLedger;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.LedgerType;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.repository.AccountLedgerRepository;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;

@ExtendWith(MockitoExtension.class)
class DepositInterestServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate FRI = LocalDate.of(2026, 11, 20);
    private static final LocalDate SAT = LocalDate.of(2026, 11, 21);
    private static final LocalDate SUN = LocalDate.of(2026, 11, 22);
    private static final LocalDateTime MON_MIDNIGHT = LocalDate.of(2026, 11, 23).atStartOfDay();

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private CompetitionAccountRepository accountRepository;

    @Mock
    private AccountLedgerRepository ledgerRepository;

    private Competition competition;

    @BeforeEach
    void setUp() {
        competition = competition(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 11), SAT);
    }

    @Test
    @DisplayName("전날(일요일) 밤 예금에 연 이율 ÷ 365를 소수 4자리에서 버려 지급하고 기록한다")
    void paysDailyInterest() {
        CompetitionAccount account = account("9000000", "1000000");
        givenActive();
        givenAccounts(account);

        int paid = service().payDailyInterest();

        // 1,000,000 × 0.03 ÷ 365 = 82.19178... → 82.1917
        assertThat(paid).isEqualTo(1);
        assertThat(account.getDeposit()).isEqualByComparingTo("1000082.1917");
        assertThat(account.getCash()).isEqualByComparingTo("9000000");
        AccountLedger ledger = savedLedgers().get(0);
        assertThat(ledger.getType()).isEqualTo(LedgerType.INTEREST);
        assertThat(ledger.getAmount()).isEqualByComparingTo("82.1917");
        assertThat(ledger.getDepositAfter()).isEqualByComparingTo("1000082.1917");
        assertThat(ledger.getInterestDate()).isEqualTo(SUN);
        assertThat(ledger.getExecutedAt()).isEqualTo(MON_MIDNIGHT);
        verify(competitionRepository).updateInterestPaidDate(competition.getCompetitionId(), SUN);
    }

    @Test
    @DisplayName("놓친 밤(토요일)은 그 밤의 예금으로 계산한다. 그 뒤 입출금은 지금 예금에서 되돌린다")
    void paysMissedNightOnThatNightsDeposit() {
        competition = competition(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 11), FRI);
        // 토요일 밤 예금 1,300,000. 일요일 17:00에 1,000,000 입금, 18:00에 300,000 출금해서 지금 2,000,000
        CompetitionAccount account = account("8000000", "2000000");
        givenActive();
        givenAccounts(account);
        when(ledgerRepository.findAllByAccountInAndExecutedAtGreaterThanEqual(anyCollection(), any()))
                .thenReturn(List.of(
                        transfer(account, LedgerType.DEPOSIT, "1000000", SUN.atTime(17, 0)),
                        transfer(account, LedgerType.WITHDRAW, "300000", SUN.atTime(18, 0))));

        int paid = service().payDailyInterest();

        // 토: 1,300,000 × 0.03 ÷ 365 = 106.8493 / 일: (2,000,000 + 106.8493) × 0.03 ÷ 365 = 164.3923
        assertThat(paid).isEqualTo(2);
        List<AccountLedger> ledgers = savedLedgers();
        assertThat(ledgers).extracting(AccountLedger::getInterestDate).containsExactly(SAT, SUN);
        assertThat(ledgers.get(0).getAmount()).isEqualByComparingTo("106.8493");
        assertThat(ledgers.get(1).getAmount()).isEqualByComparingTo("164.3923");
        assertThat(account.getDeposit()).isEqualByComparingTo("2000271.2416");
        verify(competitionRepository).updateInterestPaidDate(competition.getCompetitionId(), SUN);
    }

    @Test
    @DisplayName("이자가 0원이어도 지급한 날짜를 남겨, 나중에 처음 예금한 사람에게 지난 밤 이자를 주지 않는다")
    void zeroInterestStillAdvancesPaidDate() {
        CompetitionAccount noDeposit = account("10000000", "0");
        CompetitionAccount tinyDeposit = account("10000000", "1"); // 1 × 0.03 ÷ 365 = 0.00008...
        givenActive();
        givenAccounts(noDeposit, tinyDeposit);

        assertThat(service().payDailyInterest()).isZero();
        assertThat(tinyDeposit.getDeposit()).isEqualByComparingTo("1");
        assertThat(savedLedgers()).isEmpty();
        verify(competitionRepository).updateInterestPaidDate(competition.getCompetitionId(), SUN);
    }

    @Test
    @DisplayName("일요일에 처음 1억을 예금하면 일요일 밤 이자만 받는다")
    void firstDepositGetsOnlyThatNight() {
        CompetitionAccount account = account("0", "100000000");
        givenActive();
        givenAccounts(account);

        service().payDailyInterest();

        // 100,000,000 × 0.03 ÷ 365 = 8,219.1780 (하루치)
        assertThat(savedLedgers()).singleElement()
                .satisfies(ledger -> {
                    assertThat(ledger.getInterestDate()).isEqualTo(SUN);
                    assertThat(ledger.getAmount()).isEqualByComparingTo("8219.1780");
                });
    }

    @Test
    @DisplayName("어제 밤까지 이미 지급했으면 다시 지급하지 않는다")
    void alreadyPaid() {
        competition = competition(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 11), SUN);
        givenActive();

        assertThat(service().payDailyInterest()).isZero();
        verify(ledgerRepository, never()).saveAll(any());
        verify(competitionRepository, never()).updateInterestPaidDate(any(), any());
    }

    @Test
    @DisplayName("지급 기록이 없으면 시작일 밤부터 지급하고, 시작 전 밤은 지급하지 않는다")
    void startDateBoundary() {
        competition = competition(SUN, LocalDate.of(2026, 12, 11), null);
        givenActive();
        givenAccounts(account("9000000", "1000000"));
        service().payDailyInterest();
        assertThat(savedLedgers()).extracting(AccountLedger::getInterestDate).containsExactly(SUN);

        competition = competition(SUN.plusDays(1), LocalDate.of(2026, 12, 11), null);
        givenActive();
        assertThat(service().payDailyInterest()).isZero();
    }

    @Test
    @DisplayName("종료일 밤은 최종 평가 뒤라 지급하지 않는다")
    void endDateNightNotPaid() {
        competition = competition(LocalDate.of(2026, 11, 16), SUN, SAT);
        givenActive();

        assertThat(service().payDailyInterest()).isZero();
        verify(ledgerRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("진행 중인 대회가 없으면 아무것도 하지 않는다")
    void noActiveCompetition() {
        when(competitionRepository.findFirstByStatusOrderByStartDateDesc(CompetitionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(service().payDailyInterest()).isZero();
        verify(ledgerRepository, never()).saveAll(any());
    }

    private DepositInterestService service() {
        Clock clock = Clock.fixed(MON_MIDNIGHT.atZone(SEOUL).toInstant(), SEOUL);
        return new DepositInterestService(competitionRepository, accountRepository, ledgerRepository, clock);
    }

    private void givenActive() {
        when(competitionRepository.findFirstByStatusOrderByStartDateDesc(CompetitionStatus.ACTIVE))
                .thenReturn(Optional.of(competition));
    }

    private void givenAccounts(CompetitionAccount... accounts) {
        when(accountRepository.findAllByCompetitionAndParticipantStatus(competition, ParticipantStatus.ACTIVE))
                .thenReturn(List.of(accounts));
    }

    @SuppressWarnings("unchecked")
    private List<AccountLedger> savedLedgers() {
        ArgumentCaptor<List<AccountLedger>> ledgers = ArgumentCaptor.forClass(List.class);
        verify(ledgerRepository).saveAll(ledgers.capture());
        return ledgers.getValue();
    }

    private static AccountLedger transfer(
            CompetitionAccount account, LedgerType type, String amount, LocalDateTime executedAt) {
        return AccountLedger.builder()
                .account(account)
                .type(type)
                .amount(new BigDecimal(amount))
                .cashAfter(BigDecimal.ZERO)
                .depositAfter(BigDecimal.ZERO)
                .executedAt(executedAt)
                .build();
    }

    private static CompetitionAccount account(String cash, String deposit) {
        return CompetitionAccount.builder()
                .accountId(UUID.randomUUID())
                .cash(new BigDecimal(cash))
                .deposit(new BigDecimal(deposit))
                .build();
    }

    private static Competition competition(LocalDate startDate, LocalDate endDate, LocalDate interestPaidDate) {
        return Competition.builder()
                .status(CompetitionStatus.ACTIVE)
                .startDate(startDate)
                .endDate(endDate)
                .depositInterestRate(new BigDecimal("0.03"))
                .interestPaidDate(interestPaidDate)
                .build();
    }
}
