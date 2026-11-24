package org.sejongisc.backend.competition.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.competition.dto.DepositTransactionRequest;
import org.sejongisc.backend.competition.dto.DepositTransactionResponse;
import org.sejongisc.backend.competition.dto.DepositTransactionType;
import org.sejongisc.backend.competition.dto.UnavailableReason;
import org.sejongisc.backend.competition.entity.AccountLedger;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.repository.AccountLedgerRepository;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionParticipantRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompetitionDepositService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CompetitionRepository competitionRepository;
    private final CompetitionParticipantRepository participantRepository;
    private final CompetitionAccountRepository accountRepository;
    private final AccountLedgerRepository ledgerRepository;
    private final Clock clock;

    @Transactional
    public DepositTransactionResponse transfer(UUID competitionId, UUID userId, DepositTransactionRequest request) {
        Competition competition = findCompetition(competitionId);
        CompetitionParticipant participant = findParticipant(competition, userId);
        CompetitionAccount account = findAccount(participant);

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        validateTransferAllowed(competition, participant, now);

        BigDecimal amount = BigDecimal.valueOf(request.amount());
        if (request.type() == DepositTransactionType.DEPOSIT) {
            account.depositCash(amount);
        } else {
            account.withdrawDeposit(amount);
        }

        return DepositTransactionResponse.of(saveLedger(AccountLedger.builder()
                .account(account)
                .type(request.type().ledgerType())
                .amount(amount)
                .cashAfter(account.getCash())
                .depositAfter(account.getDeposit())
                .executedAt(now)
                .build()));
    }

    // 입출금·이자 내역을 최근순으로 반환한다. 제외된 참가자와 끝난 대회도 자기 기록은 볼 수 있다.
    @Transactional(readOnly = true)
    public Page<DepositTransactionResponse> getTransactions(UUID competitionId, UUID userId, int page, int size) {
        CompetitionAccount account = findAccount(findParticipant(findCompetition(competitionId), userId));
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return ledgerRepository.findAllByAccountOrderByExecutedAtDescInterestDateDesc(account, pageRequest)
                .map(DepositTransactionResponse::of);
    }

    private Competition findCompetition(UUID competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_NOT_FOUND));
    }

    private CompetitionParticipant findParticipant(Competition competition, UUID userId) {
        return participantRepository.findByCompetitionAndUserId(competition, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND));
    }

    private CompetitionAccount findAccount(CompetitionParticipant participant) {
        return accountRepository.findByParticipant(participant)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND));
    }

    // 대회·참가자 조건은 주문과 같다. 입출금 시간은 국내 주문 시간(16:30~21:00)과 같게 둬서
    // 자정 이자 배치와 같은 계좌를 동시에 고치지 않게 한다.
    private void validateTransferAllowed(
            Competition competition, CompetitionParticipant participant, LocalDateTime now) {
        UnavailableReason reason = OrderRule.competitionUnavailableReason(competition, now.toLocalDate());
        if (reason != null) {
            throw new CustomException(reason.getErrorCode());
        }
        if (participant.getStatus() == ParticipantStatus.EXCLUDED) {
            throw new CustomException(ErrorCode.COMPETITION_PARTICIPANT_EXCLUDED);
        }
        if (!Market.KR.isOrderOpen(now.toLocalTime())) {
            throw new CustomException(ErrorCode.COMPETITION_TRANSFER_CLOSED);
        }
    }

    // 같은 계좌의 주문·입출금과 동시에 들어오면 계좌 @Version 충돌이 난다. 다시 시도하면 되므로 409로 바꾼다.
    private AccountLedger saveLedger(AccountLedger ledger) {
        try {
            return ledgerRepository.saveAndFlush(ledger);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new CustomException(ErrorCode.COMPETITION_TRANSFER_CONFLICT);
        }
    }
}
