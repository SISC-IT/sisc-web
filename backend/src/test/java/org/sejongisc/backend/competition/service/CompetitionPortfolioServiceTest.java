package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyAccountSnapshot;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.entity.Trade;
import org.sejongisc.backend.competition.entity.TradeSide;
import org.sejongisc.backend.competition.repository.AccountLedgerRepository;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionParticipantRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.competition.repository.DailyAccountSnapshotRepository;
import org.sejongisc.backend.competition.repository.DailyClosePriceRepository;
import org.sejongisc.backend.competition.repository.HoldingRepository;
import org.sejongisc.backend.competition.repository.TradeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CompetitionPortfolioServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate FRI = LocalDate.of(2026, 11, 20);
    private static final LocalDateTime FRI_17 = FRI.atTime(17, 0);

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private CompetitionParticipantRepository participantRepository;

    @Mock
    private CompetitionAccountRepository accountRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private DailyClosePriceRepository closePriceRepository;

    @Mock
    private AccountLedgerRepository ledgerRepository;

    @Mock
    private DailyAccountSnapshotRepository snapshotRepository;

    private final UUID competitionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Competition competition;
    private CompetitionParticipant participant;
    private CompetitionAccount account;
    private CompetitionStock samsung;
    private CompetitionStock hynix;

    @BeforeEach
    void setUp() {
        competition = Competition.builder().build();
        participant = CompetitionParticipant.builder().participantId(UUID.randomUUID()).build();
        account = CompetitionAccount.builder().cash(new BigDecimal("1000000")).build();
        samsung = stock("005930", "삼성전자");
        hynix = stock("000660", "SK하이닉스");
    }

    @Test
    @DisplayName("거래 내역을 최근 체결순 페이지로 반환한다")
    void getTrades() {
        givenAccount();
        PageRequest pageRequest = PageRequest.of(1, 20);
        when(tradeRepository.findAllByAccountOrderByExecutedAtDesc(account, pageRequest))
                .thenReturn(new PageImpl<>(List.of(trade()), pageRequest, 21));

        Page<CompetitionTradeResponse> result = service().getTrades(competitionId, userId, 1, 20);

        assertThat(result.getTotalElements()).isEqualTo(21);
        CompetitionTradeResponse response = result.getContent().get(0);
        assertThat(response.ticker()).isEqualTo("005930");
        assertThat(response.companyName()).isEqualTo("삼성전자");
        assertThat(response.cashBalance()).isEqualByComparingTo("399940");
    }

    @Test
    @DisplayName("page가 음수면 0으로, size는 1~100 범위로 맞춘다")
    void getTradesClampsPageRequest() {
        givenAccount();
        when(tradeRepository.findAllByAccountOrderByExecutedAtDesc(eq(account), any()))
                .thenReturn(Page.empty());

        service().getTrades(competitionId, userId, -1, 1000);
        verify(tradeRepository).findAllByAccountOrderByExecutedAtDesc(account, PageRequest.of(0, 100));

        service().getTrades(competitionId, userId, 0, 0);
        verify(tradeRepository).findAllByAccountOrderByExecutedAtDesc(account, PageRequest.of(0, 1));
    }

    @Test
    @DisplayName("보유 종목의 평가액·손익·수익률을 최신 종가로 계산한다")
    void getHoldingsCalculatesProfit() {
        givenAccount();
        when(holdingRepository.findAllByAccount(account)).thenReturn(List.of(holding(samsung, 10, "60000")));
        // 종목별 최신순 2건. 첫 번째(11/20)가 최신이다.
        when(closePriceRepository.findLatestTwoByStockIds(List.of(samsung.getStockId()), FRI_17))
                .thenReturn(List.of(price(samsung, FRI, "63000"), price(samsung, FRI.minusDays(1), "61000")));

        List<CompetitionHoldingResponse> result = service().getHoldings(competitionId, userId);

        CompetitionHoldingResponse response = result.get(0);
        assertThat(response.ticker()).isEqualTo("005930");
        assertThat(response.companyName()).isEqualTo("삼성전자");
        assertThat(response.quantity()).isEqualTo(10L);
        assertThat(response.avgBuyPrice()).isEqualByComparingTo("60000");
        assertThat(response.closePrice()).isEqualByComparingTo("63000");
        assertThat(response.priceDate()).isEqualTo(FRI);
        // 63,000 × 10 = 630,000, 손익 630,000 − 600,000 = 30,000, 수익률 30,000 ÷ 600,000 = 0.05
        assertThat(response.evaluationAmount()).isEqualByComparingTo("630000");
        assertThat(response.profit()).isEqualByComparingTo("30000");
        assertThat(response.profitRate()).isEqualByComparingTo("0.050000");
    }

    @Test
    @DisplayName("수익률은 소수 6자리에서 반올림한다")
    void profitRateIsRoundedToSixDecimals() {
        givenAccount();
        when(holdingRepository.findAllByAccount(account)).thenReturn(List.of(holding(samsung, 3, "30000")));
        when(closePriceRepository.findLatestTwoByStockIds(anyCollection(), eq(FRI_17)))
                .thenReturn(List.of(price(samsung, FRI, "30001")));

        CompetitionHoldingResponse response = service().getHoldings(competitionId, userId).get(0);

        // 3 ÷ 90,000 = 0.0000333... → 0.000033
        assertThat(response.profitRate()).isEqualByComparingTo("0.000033");
    }

    @Test
    @DisplayName("평가액이 큰 순서로 정렬하고, 종가가 없는 종목은 평가 값을 null로 두고 맨 뒤에 둔다")
    void getHoldingsSortsByEvaluationAndKeepsNoPriceLast() {
        CompetitionStock noPrice = stock("035420", "NAVER");
        givenAccount();
        when(holdingRepository.findAllByAccount(account)).thenReturn(List.of(
                holding(noPrice, 1, "200000"),
                holding(samsung, 10, "60000"),
                holding(hynix, 5, "180000")));
        when(closePriceRepository.findLatestTwoByStockIds(anyCollection(), eq(FRI_17)))
                .thenReturn(List.of(price(samsung, FRI, "60000"), price(hynix, FRI, "190000")));

        List<CompetitionHoldingResponse> result = service().getHoldings(competitionId, userId);

        // 하이닉스 950,000 > 삼성전자 600,000 > NAVER(종가 없음)
        assertThat(result).extracting(CompetitionHoldingResponse::ticker)
                .containsExactly("000660", "005930", "035420");
        CompetitionHoldingResponse last = result.get(2);
        assertThat(last.avgBuyPrice()).isEqualByComparingTo("200000");
        assertThat(last.closePrice()).isNull();
        assertThat(last.priceDate()).isNull();
        assertThat(last.evaluationAmount()).isNull();
        assertThat(last.profit()).isNull();
        assertThat(last.profitRate()).isNull();
    }

    @Test
    @DisplayName("보유 종목이 없으면 종가를 조회하지 않고 빈 목록을 반환한다")
    void getHoldingsEmpty() {
        givenAccount();
        when(holdingRepository.findAllByAccount(account)).thenReturn(List.of());

        assertThat(service().getHoldings(competitionId, userId)).isEmpty();
        verifyNoInteractions(closePriceRepository);
    }

    @Test
    @DisplayName("오늘 현황은 PROVISIONAL이고 현금 + 예금 + 보유 종목 평가액으로 총자산·수익률을 계산한다")
    void getSummaryToday() {
        givenSummaryAccount("400000", "300000", ParticipantStatus.ACTIVE);
        when(holdingRepository.findAllByAccount(account)).thenReturn(List.of(
                holding(samsung, 10, "60000"),
                holding(hynix, 5, "180000")));
        when(closePriceRepository.findLatestTwoByStockIds(anyCollection(), eq(FRI_17)))
                .thenReturn(List.of(price(samsung, FRI, "63000"), price(hynix, FRI, "190000")));
        LocalDateTime dayStart = FRI.atStartOfDay();
        LocalDateTime dayEnd = FRI.plusDays(1).atStartOfDay();
        when(tradeRepository.sumFeeExecutedBefore(account, dayEnd)).thenReturn(new BigDecimal("60"));
        when(ledgerRepository.sumInterestForNightsBefore(account, FRI)).thenReturn(new BigDecimal("82.1917"));
        when(tradeRepository.existsByAccountAndExecutedAtLessThan(account, dayEnd)).thenReturn(true);
        when(tradeRepository.findFirstByAccountAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtAsc(
                account, dayStart, dayEnd)).thenReturn(Optional.of(tradeAt(FRI.atTime(16, 45))));
        when(tradeRepository.findFirstByAccountAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtDesc(
                account, dayStart, dayEnd)).thenReturn(Optional.of(tradeAt(FRI.atTime(16, 50))));

        CompetitionSummaryResponse summary = service().getSummary(competitionId, userId, null);

        // 평가액 630,000 + 950,000 = 1,580,000, 총자산 400,000 + 300,000 + 1,580,000 = 2,280,000
        assertThat(summary.date()).isEqualTo(FRI);
        assertThat(summary.valuationStatus()).isEqualTo(ValuationStatus.PROVISIONAL);
        assertThat(summary.valuedAt()).isEqualTo(FRI_17);
        assertThat(summary.initialBalance()).isEqualByComparingTo("2000000");
        assertThat(summary.cashBalance()).isEqualByComparingTo("400000");
        assertThat(summary.depositBalance()).isEqualByComparingTo("300000");
        assertThat(summary.stockValue()).isEqualByComparingTo("1580000");
        assertThat(summary.totalAssets()).isEqualByComparingTo("2280000");
        assertThat(summary.profitLoss()).isEqualByComparingTo("280000");
        assertThat(summary.returnRate()).isEqualByComparingTo("0.140000");
        assertThat(summary.totalFees()).isEqualByComparingTo("60");
        assertThat(summary.totalInterest()).isEqualByComparingTo("82.1917");
        assertThat(summary.participationStatus()).isEqualTo(ParticipationStatus.PARTICIPATED);
        assertThat(summary.participatedOnDate()).isTrue();
        assertThat(summary.firstParticipatedAt()).isEqualTo(FRI.atTime(16, 45));
        assertThat(summary.lastParticipatedAt()).isEqualTo(FRI.atTime(16, 50));
    }

    @Test
    @DisplayName("보유 종목이 없으면 평가액은 0이고, 손실이면 손익과 수익률이 음수다")
    void getSummaryWithoutHoldings() {
        givenSummaryAccount("1999940", "0", ParticipantStatus.ACTIVE);
        when(holdingRepository.findAllByAccount(account)).thenReturn(List.of());

        CompetitionSummaryResponse summary = service().getSummary(competitionId, userId, FRI);

        // 수수료 60원만큼 손실: 1,999,940 ÷ 2,000,000 − 1 = −0.00003
        assertThat(summary.valuationStatus()).isEqualTo(ValuationStatus.PROVISIONAL);
        assertThat(summary.stockValue()).isEqualByComparingTo("0");
        assertThat(summary.totalAssets()).isEqualByComparingTo("1999940");
        assertThat(summary.profitLoss()).isEqualByComparingTo("-60");
        assertThat(summary.returnRate()).isEqualByComparingTo("-0.000030");
        verifyNoInteractions(closePriceRepository);
    }

    @Test
    @DisplayName("종가가 없는 보유 종목이 있으면 임의로 평가하지 않고 PENDING_DATA로 평가 항목을 비운다")
    void getSummaryPendingWhenPriceMissing() {
        givenSummaryAccount("1400000", "0", ParticipantStatus.ACTIVE);
        when(holdingRepository.findAllByAccount(account)).thenReturn(List.of(holding(samsung, 10, "60000")));
        when(closePriceRepository.findLatestTwoByStockIds(anyCollection(), eq(FRI_17))).thenReturn(List.of());

        CompetitionSummaryResponse summary = service().getSummary(competitionId, userId, null);

        assertThat(summary.valuationStatus()).isEqualTo(ValuationStatus.PENDING_DATA);
        assertThat(summary.cashBalance()).isEqualByComparingTo("1400000");
        assertThat(summary.stockValue()).isNull();
        assertThat(summary.totalAssets()).isNull();
        assertThat(summary.profitLoss()).isNull();
        assertThat(summary.returnRate()).isNull();
        assertThat(summary.valuedAt()).isNull();
    }

    @Test
    @DisplayName("지난 날짜는 그날 마감 기준이다. 스냅샷 총자산에서 스냅샷 이후 저녁 거래 수수료를 빼고, 날짜별 잔액은 비운다")
    void getSummaryPastDateIsEndOfDay() {
        givenSummaryAccount("400000", "300000", ParticipantStatus.ACTIVE);
        LocalDate thu = FRI.minusDays(1);
        LocalDateTime snapshotAt = thu.atTime(16, 5);
        DailyAccountSnapshot snapshot = DailyAccountSnapshot.builder()
                .account(account).snapshotDate(thu)
                .totalValue(new BigDecimal("2100000")).profitRate(new BigDecimal("0.050000")).rank(3)
                .build();
        snapshot.setCreatedDate(snapshotAt);
        when(snapshotRepository.findByAccountAndSnapshotDate(account, thu)).thenReturn(Optional.of(snapshot));
        // 목요일까지 수수료 30원 중 20원은 스냅샷 전, 10원은 그날 저녁 거래분
        when(tradeRepository.sumFeeExecutedBefore(account, FRI.atStartOfDay())).thenReturn(new BigDecimal("30"));
        when(tradeRepository.sumFeeExecutedBefore(account, snapshotAt)).thenReturn(new BigDecimal("20"));

        CompetitionSummaryResponse summary = service().getSummary(competitionId, userId, thu);

        // 2,100,000 − 10 = 2,099,990, 수익률 2,099,990 ÷ 2,000,000 − 1 = 0.049995
        assertThat(summary.date()).isEqualTo(thu);
        assertThat(summary.valuationStatus()).isEqualTo(ValuationStatus.FINALIZED);
        assertThat(summary.valuedAt()).isEqualTo(snapshotAt);
        assertThat(summary.totalAssets()).isEqualByComparingTo("2099990");
        assertThat(summary.profitLoss()).isEqualByComparingTo("99990");
        assertThat(summary.returnRate()).isEqualByComparingTo("0.049995");
        assertThat(summary.totalFees()).isEqualByComparingTo("30");
        assertThat(summary.cashBalance()).isNull();
        assertThat(summary.depositBalance()).isNull();
        assertThat(summary.stockValue()).isNull();
        verifyNoInteractions(holdingRepository, closePriceRepository);
    }

    @Test
    @DisplayName("스냅샷이 없는 지난 날짜(주말 등)는 PENDING_DATA다")
    void getSummaryPastDateWithoutSnapshot() {
        givenSummaryAccount("400000", "300000", ParticipantStatus.ACTIVE);
        LocalDate sat = FRI.minusDays(6);
        when(snapshotRepository.findByAccountAndSnapshotDate(account, sat)).thenReturn(Optional.empty());

        CompetitionSummaryResponse summary = service().getSummary(competitionId, userId, sat);

        assertThat(summary.valuationStatus()).isEqualTo(ValuationStatus.PENDING_DATA);
        assertThat(summary.totalAssets()).isNull();
        assertThat(summary.valuedAt()).isNull();
    }

    @Test
    @DisplayName("오늘 이후 날짜는 400")
    void getSummaryRejectsFutureDate() {
        givenSummaryAccount("400000", "300000", ParticipantStatus.ACTIVE);

        assertThatThrownBy(() -> service().getSummary(competitionId, userId, FRI.plusDays(1)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("매수·매도를 한 번도 안 했으면 NOT_PARTICIPATED, 제외됐으면 EXCLUDED")
    void participationStatus() {
        givenSummaryAccount("2000000", "0", ParticipantStatus.ACTIVE);
        when(holdingRepository.findAllByAccount(account)).thenReturn(List.of());
        CompetitionSummaryResponse notYet = service().getSummary(competitionId, userId, null);
        assertThat(notYet.participationStatus()).isEqualTo(ParticipationStatus.NOT_PARTICIPATED);
        assertThat(notYet.participatedOnDate()).isFalse();
        assertThat(notYet.firstParticipatedAt()).isNull();

        givenSummaryAccount("2000000", "0", ParticipantStatus.EXCLUDED);
        when(holdingRepository.findAllByAccount(account)).thenReturn(List.of());
        assertThat(service().getSummary(competitionId, userId, null).participationStatus())
                .isEqualTo(ParticipationStatus.EXCLUDED);
    }

    @Test
    @DisplayName("대회가 없으면 COMPETITION_NOT_FOUND")
    void competitionNotFound() {
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getHoldings(competitionId, userId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_NOT_FOUND);
    }

    @Test
    @DisplayName("참가자가 아니면 COMPETITION_PARTICIPANT_NOT_FOUND")
    void notParticipant() {
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.of(competition));
        when(participantRepository.findByCompetitionAndUserId(competition, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getTrades(competitionId, userId, 0, 20))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND);
        verifyNoInteractions(tradeRepository);
    }

    private CompetitionPortfolioService service() {
        Clock clock = Clock.fixed(FRI_17.atZone(SEOUL).toInstant(), SEOUL);
        return new CompetitionPortfolioService(competitionRepository, participantRepository, accountRepository,
                tradeRepository, holdingRepository, closePriceRepository, ledgerRepository, snapshotRepository, clock);
    }

    private void givenSummaryAccount(String cash, String deposit, ParticipantStatus status) {
        competition = Competition.builder().initialCash(new BigDecimal("2000000")).build();
        participant = CompetitionParticipant.builder()
                .participantId(UUID.randomUUID()).competition(competition).status(status).build();
        account = CompetitionAccount.builder()
                .participant(participant)
                .cash(new BigDecimal(cash))
                .deposit(new BigDecimal(deposit))
                .build();
        givenAccount();
    }

    private Trade tradeAt(LocalDateTime executedAt) {
        return Trade.builder().account(account).stock(samsung).executedAt(executedAt).build();
    }

    private void givenAccount() {
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.of(competition));
        when(participantRepository.findByCompetitionAndUserId(competition, userId)).thenReturn(Optional.of(participant));
        when(accountRepository.findByParticipant(participant)).thenReturn(Optional.of(account));
    }

    private Trade trade() {
        return Trade.builder()
                .tradeId(UUID.randomUUID())
                .account(account)
                .stock(samsung)
                .side(TradeSide.BUY)
                .quantity(10L)
                .price(new BigDecimal("60000"))
                .fee(new BigDecimal("60"))
                .cashAfter(new BigDecimal("399940"))
                .executedAt(FRI_17)
                .build();
    }

    private Holding holding(CompetitionStock stock, long quantity, String avgBuyPrice) {
        return Holding.builder()
                .account(account)
                .stock(stock)
                .quantity(quantity)
                .avgBuyPrice(new BigDecimal(avgBuyPrice))
                .build();
    }

    private static CompetitionStock stock(String ticker, String name) {
        return CompetitionStock.builder()
                .stockId(UUID.randomUUID())
                .ticker(ticker)
                .name(name)
                .market(Market.KR)
                .build();
    }

    private static DailyClosePrice price(CompetitionStock stock, LocalDate date, String closePrice) {
        return DailyClosePrice.builder()
                .closePriceId(UUID.randomUUID())
                .stock(stock)
                .priceDate(date)
                .availableAt(date.atTime(16, 30))
                .closePrice(new BigDecimal(closePrice))
                .build();
    }
}
