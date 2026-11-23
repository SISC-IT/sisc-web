package org.sejongisc.backend.competition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.competition.entity.AccountLedger;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.LedgerType;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.repository.AccountLedgerRepository;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositInterestService {

    private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);

    private final CompetitionRepository competitionRepository;
    private final CompetitionAccountRepository accountRepository;
    private final AccountLedgerRepository ledgerRepository;
    private final Clock clock;

    // 자정이 지나면 전날 밤 예금에 하루치 이자(연 이율 ÷ 365)를 지급한다. 주말·공휴일도 지급한다.
    // 서버가 꺼져 있어 놓친 밤이 있으면 그 밤부터 순서대로 함께 지급한다.
    @Transactional
    public int payDailyInterest() {
        Optional<Competition> active =
                competitionRepository.findFirstByStatusOrderByStartDateDesc(CompetitionStatus.ACTIVE);
        if (active.isEmpty()) {
            return 0;
        }
        Competition competition = active.get();
        // 시작일 밤부터 종료 전날 밤까지 지급한다. 종료일 밤은 최종 평가 뒤라 지급하지 않는다.
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        LocalDate lastNight = yesterday.isBefore(competition.getEndDate()) ? yesterday : competition.getEndDate().minusDays(1);
        LocalDate firstUnpaid = competition.getInterestPaidDate() == null
                ? competition.getStartDate()
                : competition.getInterestPaidDate().plusDays(1);
        if (firstUnpaid.isAfter(lastNight)) {
            return 0;
        }

        List<CompetitionAccount> accounts =
                accountRepository.findAllByCompetitionAndParticipantStatus(competition, ParticipantStatus.ACTIVE);
        Map<UUID, List<AccountLedger>> transfersAfter = transfersAfter(accounts, firstUnpaid.plusDays(1).atStartOfDay());
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        List<AccountLedger> ledgers = new ArrayList<>();
        for (LocalDate night = firstUnpaid; !night.isAfter(lastNight); night = night.plusDays(1)) {
            for (CompetitionAccount account : accounts) {
                List<AccountLedger> transfers = transfersAfter.getOrDefault(account.getAccountId(), List.of());
                BigDecimal nightDeposit = account.getDeposit().subtract(movedSince(transfers, night.plusDays(1)));
                payInterest(account, nightDeposit, competition.getDepositInterestRate(), night, now)
                        .ifPresent(ledgers::add);
            }
        }
        ledgerRepository.saveAll(ledgers);
        competitionRepository.updateInterestPaidDate(competition.getCompetitionId(), lastNight);
        log.info("[예금 이자] 완료: competitionId={}, interestDate={}~{}, 기록={}",
                competition.getCompetitionId(), firstUnpaid, lastNight, ledgers.size());
        return ledgers.size();
    }

    private Map<UUID, List<AccountLedger>> transfersAfter(List<CompetitionAccount> accounts, LocalDateTime from) {
        if (accounts.isEmpty()) {
            return Map.of();
        }
        return ledgerRepository.findAllByAccountInAndExecutedAtGreaterThanEqual(accounts, from).stream()
                .collect(Collectors.groupingBy(ledger -> ledger.getAccount().getAccountId()));
    }

    // 그 밤이 지난 뒤(다음 날 0시부터) 예금에 넣은 금액 − 뺀 금액. 지금 예금에서 이 값을 빼면 그 밤의 예금이다.
    // 놓친 밤의 이자는 아직 지급 전이라 여기에 없고, 앞 밤 이자는 이 반복에서 예금에 먼저 더해져 복리가 맞는다.
    private static BigDecimal movedSince(List<AccountLedger> transfers, LocalDate day) {
        LocalDateTime from = day.atStartOfDay();
        return transfers.stream()
                .filter(ledger -> !ledger.getExecutedAt().isBefore(from))
                .map(ledger -> switch (ledger.getType()) {
                    case DEPOSIT -> ledger.getAmount();
                    case WITHDRAW -> ledger.getAmount().negate();
                    case INTEREST -> BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // 수수료와 같이 소수 4자리에서 버린다. 버려서 0이 되면 기록하지 않는다.
    private Optional<AccountLedger> payInterest(CompetitionAccount account, BigDecimal nightDeposit,
                                                BigDecimal annualRate, LocalDate interestDate, LocalDateTime now) {
        BigDecimal interest = nightDeposit.multiply(annualRate)
                .divide(DAYS_PER_YEAR, 4, RoundingMode.DOWN);
        if (interest.signum() == 0) {
            return Optional.empty();
        }
        account.addInterest(interest);
        return Optional.of(AccountLedger.builder()
                .account(account)
                .type(LedgerType.INTEREST)
                .amount(interest)
                .cashAfter(account.getCash())
                .depositAfter(account.getDeposit())
                .interestDate(interestDate)
                .executedAt(now)
                .build());
    }
}
