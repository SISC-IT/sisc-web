package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.competition.dto.DepositTransactionRequest;
import org.sejongisc.backend.competition.dto.DepositTransactionResponse;
import org.sejongisc.backend.competition.dto.DepositTransactionType;
import org.sejongisc.backend.competition.entity.AccountLedger;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.LedgerType;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.repository.AccountLedgerRepository;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionParticipantRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class CompetitionDepositServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime MON_17 = LocalDate.of(2026, 11, 23).atTime(17, 0);

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private CompetitionParticipantRepository participantRepository;

    @Mock
    private CompetitionAccountRepository accountRepository;

    @Mock
    private AccountLedgerRepository ledgerRepository;

    private final UUID competitionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Competition competition;
    private CompetitionParticipant participant;
    private CompetitionAccount account;

    @BeforeEach
    void setUp() {
        competition = competition(CompetitionStatus.ACTIVE);
        participant = CompetitionParticipant.builder()
                .participantId(UUID.randomUUID())
                .status(ParticipantStatus.ACTIVE)
                .build();
        account = CompetitionAccount.builder()
                .cash(new BigDecimal("1000000"))
                .deposit(new BigDecimal("300000"))
                .build();
    }

    @Test
    @DisplayName("입금하면 현금을 예금으로 옮기고 이동 후 잔액을 기록한다")
    void deposit() {
        givenAccount();
        givenLedgerSaved();

        DepositTransactionResponse response = service(MON_17).transfer(competitionId, userId, request(DepositTransactionType.DEPOSIT, 400000));

        assertThat(account.getCash()).isEqualByComparingTo("600000");
        assertThat(account.getDeposit()).isEqualByComparingTo("700000");
        AccountLedger ledger = savedLedger();
        assertThat(ledger.getType()).isEqualTo(LedgerType.DEPOSIT);
        assertThat(ledger.getAmount()).isEqualByComparingTo("400000");
        assertThat(ledger.getCashAfter()).isEqualByComparingTo("600000");
        assertThat(ledger.getDepositAfter()).isEqualByComparingTo("700000");
        assertThat(ledger.getInterestDate()).isNull();
        assertThat(ledger.getExecutedAt()).isEqualTo(MON_17);
        assertThat(response.cashBalance()).isEqualByComparingTo("600000");
        assertThat(response.depositBalance()).isEqualByComparingTo("700000");
    }

    @Test
    @DisplayName("출금하면 예금을 현금으로 옮긴다")
    void withdraw() {
        givenAccount();
        givenLedgerSaved();

        service(MON_17).transfer(competitionId, userId, request(DepositTransactionType.WITHDRAW, 300000));

        assertThat(account.getCash()).isEqualByComparingTo("1300000");
        assertThat(account.getDeposit()).isEqualByComparingTo("0");
        assertThat(savedLedger().getType()).isEqualTo(LedgerType.WITHDRAW);
    }

    @Test
    @DisplayName("주말에도 16:30~21:00에는 입출금할 수 있다")
    void weekendAllowed() {
        givenAccount();
        givenLedgerSaved();
        LocalDateTime saturday17 = LocalDate.of(2026, 11, 21).atTime(17, 0);

        service(saturday17).transfer(competitionId, userId, request(DepositTransactionType.DEPOSIT, 1));

        assertThat(account.getDeposit()).isEqualByComparingTo("300001");
    }

    @Test
    @DisplayName("예금보다 많이 출금하면 예금 부족이고 기록하지 않는다")
    void insufficientDeposit() {
        givenAccount();

        assertErrorCode(() -> service(MON_17).transfer(competitionId, userId, request(DepositTransactionType.WITHDRAW, 300001)),
                ErrorCode.COMPETITION_INSUFFICIENT_DEPOSIT);
        verifyNoInteractions(ledgerRepository);
    }

    @Test
    @DisplayName("21:00부터는 입출금할 수 없다")
    void transferClosed() {
        givenAccount();

        assertErrorCode(() -> service(MON_17.withHour(21)).transfer(competitionId, userId, request(DepositTransactionType.DEPOSIT, 1)),
                ErrorCode.COMPETITION_TRANSFER_CLOSED);
        assertThat(account.getCash()).isEqualByComparingTo("1000000");
        verifyNoInteractions(ledgerRepository);
    }

    @Test
    @DisplayName("진행 중인 대회가 아니면 입출금할 수 없다")
    void competitionNotActive() {
        competition = competition(CompetitionStatus.READY);
        givenAccount();

        assertErrorCode(() -> service(MON_17).transfer(competitionId, userId, request(DepositTransactionType.DEPOSIT, 1)),
                ErrorCode.COMPETITION_NOT_ACTIVE);
    }

    @Test
    @DisplayName("제외된 참가자는 입출금할 수 없다")
    void excludedParticipant() {
        participant = CompetitionParticipant.builder().status(ParticipantStatus.EXCLUDED).build();
        givenAccount();

        assertErrorCode(() -> service(MON_17).transfer(competitionId, userId, request(DepositTransactionType.DEPOSIT, 1)),
                ErrorCode.COMPETITION_PARTICIPANT_EXCLUDED);
    }

    @Test
    @DisplayName("같은 계좌의 주문·입출금과 겹쳐 버전 충돌이 나면 409")
    void optimisticLockConflict() {
        givenAccount();
        when(ledgerRepository.saveAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(CompetitionAccount.class, UUID.randomUUID()));

        assertErrorCode(() -> service(MON_17).transfer(competitionId, userId, request(DepositTransactionType.DEPOSIT, 1)),
                ErrorCode.COMPETITION_TRANSFER_CONFLICT);
    }

    @Test
    @DisplayName("예금 내역을 최근순 페이지로 반환한다. 제외된 참가자도 조회할 수 있다")
    void getTransactions() {
        participant = CompetitionParticipant.builder().status(ParticipantStatus.EXCLUDED).build();
        givenAccount();
        AccountLedger interest = AccountLedger.builder()
                .account(account).type(LedgerType.INTEREST).amount(new BigDecimal("82.1917"))
                .cashAfter(new BigDecimal("1000000")).depositAfter(new BigDecimal("300082.1917"))
                .interestDate(LocalDate.of(2026, 11, 22)).executedAt(MON_17.withHour(0))
                .build();
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(ledgerRepository.findAllByAccountOrderByExecutedAtDescInterestDateDesc(account, pageRequest))
                .thenReturn(new PageImpl<>(List.of(interest), pageRequest, 1));

        Page<DepositTransactionResponse> result = service(MON_17).getTransactions(competitionId, userId, 0, 20);

        DepositTransactionResponse response = result.getContent().get(0);
        assertThat(response.type()).isEqualTo(LedgerType.INTEREST);
        assertThat(response.amount()).isEqualByComparingTo("82.1917");
        assertThat(response.interestDate()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(response.depositBalance()).isEqualByComparingTo("300082.1917");
    }

    @Test
    @DisplayName("예금 내역 page가 음수면 0으로, size는 1~100 범위로 맞춘다")
    void getTransactionsClampsPageRequest() {
        givenAccount();
        when(ledgerRepository.findAllByAccountOrderByExecutedAtDescInterestDateDesc(any(), any()))
                .thenReturn(Page.empty());

        service(MON_17).getTransactions(competitionId, userId, -1, 1000);
        verify(ledgerRepository).findAllByAccountOrderByExecutedAtDescInterestDateDesc(account, PageRequest.of(0, 100));
    }

    private CompetitionDepositService service(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL);
        return new CompetitionDepositService(
                competitionRepository, participantRepository, accountRepository, ledgerRepository, clock);
    }

    private void givenAccount() {
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.of(competition));
        when(participantRepository.findByCompetitionAndUserId(competition, userId)).thenReturn(Optional.of(participant));
        when(accountRepository.findByParticipant(participant)).thenReturn(Optional.of(account));
    }

    private void givenLedgerSaved() {
        when(ledgerRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AccountLedger savedLedger() {
        ArgumentCaptor<AccountLedger> ledger = ArgumentCaptor.forClass(AccountLedger.class);
        verify(ledgerRepository).saveAndFlush(ledger.capture());
        return ledger.getValue();
    }

    private static DepositTransactionRequest request(DepositTransactionType type, long amount) {
        return new DepositTransactionRequest(type, amount);
    }

    private static Competition competition(CompetitionStatus status) {
        return Competition.builder()
                .status(status)
                .startDate(LocalDate.of(2026, 11, 16))
                .endDate(LocalDate.of(2026, 12, 11))
                .build();
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }
}
