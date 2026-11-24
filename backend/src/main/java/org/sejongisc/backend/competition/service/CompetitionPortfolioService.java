package org.sejongisc.backend.competition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.competition.dto.CompetitionHoldingResponse;
import org.sejongisc.backend.competition.dto.CompetitionSummaryResponse;
import org.sejongisc.backend.competition.dto.CompetitionSummaryResponse.ParticipationStatus;
import org.sejongisc.backend.competition.dto.CompetitionSummaryResponse.ValuationStatus;
import org.sejongisc.backend.competition.dto.CompetitionTradeResponse;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.DailyAccountSnapshot;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.entity.Trade;
import org.sejongisc.backend.competition.repository.AccountLedgerRepository;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionParticipantRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.competition.repository.DailyAccountSnapshotRepository;
import org.sejongisc.backend.competition.repository.DailyClosePriceRepository;
import org.sejongisc.backend.competition.repository.HoldingRepository;
import org.sejongisc.backend.competition.repository.TradeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompetitionPortfolioService {

    private static final int MAX_PAGE_SIZE = 100;

    // 평가액이 큰 순서. 종가가 없어 평가액이 null인 종목은 뒤로 보낸다.
    private static final Comparator<CompetitionHoldingResponse> BY_EVALUATION_DESC = Comparator.comparing(
            CompetitionHoldingResponse::evaluationAmount, Comparator.nullsLast(Comparator.reverseOrder()));

    private final CompetitionRepository competitionRepository;
    private final CompetitionParticipantRepository participantRepository;
    private final CompetitionAccountRepository accountRepository;
    private final TradeRepository tradeRepository;
    private final HoldingRepository holdingRepository;
    private final DailyClosePriceRepository closePriceRepository;
    private final AccountLedgerRepository ledgerRepository;
    private final DailyAccountSnapshotRepository snapshotRepository;
    private final Clock clock;

    public Page<CompetitionTradeResponse> getTrades(UUID competitionId, UUID userId, int page, int size) {
        CompetitionAccount account = findAccount(competitionId, userId);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return tradeRepository.findAllByAccountOrderByExecutedAtDesc(account, pageRequest)
                .map(CompetitionTradeResponse::of);
    }

    public List<CompetitionHoldingResponse> getHoldings(UUID competitionId, UUID userId) {
        return holdingResponses(findAccount(competitionId, userId));
    }

    // 그날의 자산·수익률·참여 상태. 날짜를 생략하면 오늘이다.
    // 오늘은 지금 잔액으로 평가하고, 지난 날짜는 그날 순위 스냅샷이 있으면 그 값을 쓴다.
    // 계좌와 보유 종목을 따로 읽는 사이에 주문이 체결되면 현금과 보유가 어긋나므로, 같은 시점 데이터로 읽는다.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CompetitionSummaryResponse getSummary(UUID competitionId, UUID userId, LocalDate date) {
        CompetitionAccount account = findAccount(competitionId, userId);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        LocalDate today = now.toLocalDate();
        LocalDate day = date == null ? today : date;
        if (day.isAfter(today)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        CompetitionSummaryResponse.CompetitionSummaryResponseBuilder summary = activityOn(account, day)
                .date(day)
                .initialBalance(account.getParticipant().getCompetition().getInitialCash());
        if (day.isBefore(today)) {
            return pastDay(summary, account, day);
        }

        summary.cashBalance(account.getCash()).depositBalance(account.getDeposit());
        List<CompetitionHoldingResponse> holdings = holdingResponses(account);
        // 종가가 없는 보유 종목이 있으면 총자산을 정할 수 없다. 임의의 값을 넣지 않고 평가 항목을 비운다.
        List<String> unpriced = holdings.stream()
                .filter(holding -> holding.evaluationAmount() == null)
                .map(CompetitionHoldingResponse::ticker)
                .toList();
        if (!unpriced.isEmpty()) {
            log.warn("[내 현황] 종가가 없는 보유 종목이 있어 평가하지 않았습니다: tickers={}", unpriced);
            return summary.valuationStatus(ValuationStatus.PENDING_DATA).build();
        }
        return valued(summary, account, holdings, now);
    }

    // 날짜별 현금·예금·주식 평가액은 남기지 않아 비운다. 총자산과 수익률은 그날 순위 스냅샷(16:00)에서 읽는다.
    private CompetitionSummaryResponse pastDay(
            CompetitionSummaryResponse.CompetitionSummaryResponseBuilder summary, CompetitionAccount account,
            LocalDate day) {
        Optional<DailyAccountSnapshot> snapshot = snapshotRepository.findByAccountAndSnapshotDate(account, day);
        if (snapshot.isEmpty()) {
            return summary.valuationStatus(ValuationStatus.PENDING_DATA).build();
        }
        BigDecimal initialCash = account.getParticipant().getCompetition().getInitialCash();
        return summary
                .valuationStatus(ValuationStatus.FINALIZED)
                .valuedAt(snapshot.get().getCreatedDate())
                .totalAssets(snapshot.get().getTotalValue())
                .profitLoss(snapshot.get().getTotalValue().subtract(initialCash))
                .returnRate(snapshot.get().getProfitRate())
                .build();
    }

    // 총자산 = 현금 + 예금 + 보유 종목 평가액. 수익률은 순위 스냅샷과 같은 식(총자산 ÷ 초기 자금 − 1)이다.
    private static CompetitionSummaryResponse valued(
            CompetitionSummaryResponse.CompetitionSummaryResponseBuilder summary, CompetitionAccount account,
            List<CompetitionHoldingResponse> holdings, LocalDateTime now) {
        BigDecimal stockValue = holdings.stream()
                .map(CompetitionHoldingResponse::evaluationAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAssets = account.getCash().add(account.getDeposit()).add(stockValue);
        BigDecimal initialCash = account.getParticipant().getCompetition().getInitialCash();
        return summary
                .valuationStatus(ValuationStatus.PROVISIONAL)
                .valuedAt(now)
                .stockValue(stockValue)
                .totalAssets(totalAssets)
                .profitLoss(totalAssets.subtract(initialCash))
                .returnRate(totalAssets.divide(initialCash, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE))
                .build();
    }

    // 날짜와 상관없이 기록으로 알 수 있는 항목: 그날 기준 누적 수수료·이자, 참여 상태, 그날 거래 시각
    private CompetitionSummaryResponse.CompetitionSummaryResponseBuilder activityOn(
            CompetitionAccount account, LocalDate day) {
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
        Optional<Trade> first = tradeRepository
                .findFirstByAccountAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtAsc(
                        account, dayStart, dayEnd);
        Optional<Trade> last = tradeRepository
                .findFirstByAccountAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtDesc(
                        account, dayStart, dayEnd);
        return CompetitionSummaryResponse.builder()
                .totalFees(tradeRepository.sumFeeExecutedBefore(account, dayEnd))
                .totalInterest(ledgerRepository.sumInterestForNightsBefore(account, day))
                .participationStatus(participationStatus(account, dayEnd))
                .participatedOnDate(first.isPresent())
                .firstParticipatedAt(first.map(Trade::getExecutedAt).orElse(null))
                .lastParticipatedAt(last.map(Trade::getExecutedAt).orElse(null));
    }

    // 그날까지 매수·매도를 한 번이라도 했는지. 예금 입출금은 참여로 치지 않는다.
    // 제외 이력은 남기지 않아 EXCLUDED는 날짜와 상관없이 지금 상태를 따른다.
    private ParticipationStatus participationStatus(CompetitionAccount account, LocalDateTime dayEnd) {
        if (account.getParticipant().getStatus() == ParticipantStatus.EXCLUDED) {
            return ParticipationStatus.EXCLUDED;
        }
        return tradeRepository.existsByAccountAndExecutedAtLessThan(account, dayEnd)
                ? ParticipationStatus.PARTICIPATED
                : ParticipationStatus.NOT_PARTICIPATED;
    }

    private List<CompetitionHoldingResponse> holdingResponses(CompetitionAccount account) {
        List<Holding> holdings = holdingRepository.findAllByAccount(account);
        Map<UUID, DailyClosePrice> latestPrices = latestPrices(holdings);
        return holdings.stream()
                .map(holding -> CompetitionHoldingResponse.of(
                        holding, latestPrices.get(holding.getStock().getStockId())))
                .sorted(BY_EVALUATION_DESC)
                .toList();
    }

    private CompetitionAccount findAccount(UUID competitionId, UUID userId) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_NOT_FOUND));
        CompetitionParticipant participant = participantRepository.findByCompetitionAndUserId(competition, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND));
        return accountRepository.findByParticipant(participant)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND));
    }

    // 종목 목록 화면과 같은 기준(지금 공개된 종가)으로 종목별 최신 종가를 한 번에 읽는다.
    private Map<UUID, DailyClosePrice> latestPrices(List<Holding> holdings) {
        if (holdings.isEmpty()) {
            return Map.of();
        }
        List<UUID> stockIds = holdings.stream().map(holding -> holding.getStock().getStockId()).toList();
        // 쿼리는 종목별로 최신순 2건을 준다. 먼저 나온 최신 종가만 남긴다.
        return closePriceRepository.findLatestTwoByStockIds(stockIds, LocalDateTime.now(clock)).stream()
                .collect(Collectors.toMap(
                        price -> price.getStock().getStockId(), price -> price, (latest, older) -> latest));
    }
}
