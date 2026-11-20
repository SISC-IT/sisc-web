package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.competition.dto.CompetitionTradeRequest;
import org.sejongisc.backend.competition.dto.CompetitionTradeResponse;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.entity.Trade;
import org.sejongisc.backend.competition.entity.TradeSide;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionParticipantRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.competition.repository.CompetitionStockRepository;
import org.sejongisc.backend.competition.repository.DailyClosePriceRepository;
import org.sejongisc.backend.competition.repository.HoldingRepository;
import org.sejongisc.backend.competition.repository.TradeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class CompetitionTradeServiceTest {

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
    private CompetitionStockRepository stockRepository;

    @Mock
    private DailyClosePriceRepository closePriceRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private TradeRepository tradeRepository;

    private final UUID competitionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID key = UUID.randomUUID();
    private Competition competition;
    private CompetitionParticipant participant;
    private CompetitionAccount account;
    private CompetitionStock samsung;
    private DailyClosePrice todayPrice;

    @BeforeEach
    void setUp() {
        competition = competition(CompetitionStatus.ACTIVE, "0.000100");
        participant = CompetitionParticipant.builder()
                .participantId(UUID.randomUUID())
                .status(ParticipantStatus.ACTIVE)
                .build();
        account = CompetitionAccount.builder().cash(new BigDecimal("1000000.0000")).build();
        samsung = CompetitionStock.builder()
                .stockId(UUID.randomUUID())
                .ticker("005930")
                .name("삼성전자")
                .market(Market.KR)
                .build();
        todayPrice = price(samsung, FRI, "60000.0000");
    }

    @Test
    @DisplayName("처음 매수하면 대금과 수수료를 현금에서 빼고 보유 종목을 새로 만든다")
    void buyCreatesHolding() {
        givenOrderable();
        when(holdingRepository.findByAccountAndStock(account, samsung)).thenReturn(Optional.empty());
        givenTradeSaved();

        CompetitionTradeResponse response = service(FRI_17).order(competitionId, userId, key, buy(10));

        // 60,000 × 10 = 600,000, 수수료 600,000 × 0.0001 = 60
        assertThat(account.getCash()).isEqualByComparingTo("399940");
        ArgumentCaptor<Holding> holding = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(holding.capture());
        assertThat(holding.getValue().getQuantity()).isEqualTo(10L);
        assertThat(holding.getValue().getAvgBuyPrice()).isEqualByComparingTo("60000");

        Trade trade = savedTrade();
        assertThat(trade.getSide()).isEqualTo(TradeSide.BUY);
        assertThat(trade.getQuantity()).isEqualTo(10L);
        assertThat(trade.getFee()).isEqualByComparingTo("60");
        assertThat(trade.getClosePrice()).isSameAs(todayPrice);
        assertThat(trade.getPrice()).isEqualByComparingTo("60000");
        assertThat(trade.getIdempotencyKey()).isEqualTo(key);
        assertThat(trade.getExecutedAt()).isEqualTo(FRI_17);

        assertThat(response.ticker()).isEqualTo("005930");
        assertThat(response.price()).isEqualByComparingTo("60000");
        assertThat(response.fee()).isEqualByComparingTo("60");
        assertThat(response.cashBalance()).isEqualByComparingTo("399940");
    }

    @Test
    @DisplayName("이미 보유한 종목을 매수하면 수량을 늘리고 평균 매입가를 다시 계산한다")
    void buyUpdatesExistingHolding() {
        givenOrderable();
        Holding holding = Holding.builder().quantity(10L).avgBuyPrice(new BigDecimal("50000")).build();
        when(holdingRepository.findByAccountAndStock(account, samsung)).thenReturn(Optional.of(holding));
        givenTradeSaved();

        service(FRI_17).order(competitionId, userId, key, buy(10));

        assertThat(holding.getQuantity()).isEqualTo(20L);
        assertThat(holding.getAvgBuyPrice()).isEqualByComparingTo("55000");
        verify(holdingRepository, never()).save(any());
    }

    @Test
    @DisplayName("일부 매도하면 대금에서 수수료를 뺀 금액을 현금에 넣고 보유 수량을 줄인다")
    void sellPartially() {
        givenOrderable();
        Holding holding = Holding.builder().quantity(10L).avgBuyPrice(new BigDecimal("50000")).build();
        when(holdingRepository.findByAccountAndStock(account, samsung)).thenReturn(Optional.of(holding));
        givenTradeSaved();

        service(FRI_17).order(competitionId, userId, key, sell(4));

        // 60,000 × 4 = 240,000, 수수료 24
        assertThat(account.getCash()).isEqualByComparingTo("1239976");
        assertThat(holding.getQuantity()).isEqualTo(6L);
        verify(holdingRepository, never()).delete(any());
        assertThat(savedTrade().getSide()).isEqualTo(TradeSide.SELL);
    }

    @Test
    @DisplayName("전량 매도하면 보유 종목 행을 삭제한다")
    void sellAllDeletesHolding() {
        givenOrderable();
        Holding holding = Holding.builder().quantity(10L).avgBuyPrice(new BigDecimal("50000")).build();
        when(holdingRepository.findByAccountAndStock(account, samsung)).thenReturn(Optional.of(holding));
        givenTradeSaved();

        service(FRI_17).order(competitionId, userId, key, sell(10));

        verify(holdingRepository).delete(holding);
    }

    @Test
    @DisplayName("수수료는 소수 4자리에서 버린다")
    void feeIsRoundedDown() {
        competition = competition(CompetitionStatus.ACTIVE, "0.000015");
        todayPrice = price(samsung, FRI, "59999.0000");
        givenOrderable();
        when(holdingRepository.findByAccountAndStock(account, samsung)).thenReturn(Optional.empty());
        givenTradeSaved();

        service(FRI_17).order(competitionId, userId, key, buy(1));

        // 59,999 × 0.000015 = 0.899985 → 0.8999
        assertThat(savedTrade().getFee()).isEqualByComparingTo("0.8999");
    }

    @Test
    @DisplayName("같은 키로 같은 주문이 다시 오면 새로 체결하지 않고 기존 거래로 응답한다")
    void sameKeySameOrderReturnsExistingTrade() {
        givenAccount();
        Trade existing = existingTrade(TradeSide.BUY, 10L, todayPrice.getClosePriceId());
        when(tradeRepository.findByAccountAndIdempotencyKey(account, key)).thenReturn(Optional.of(existing));

        CompetitionTradeResponse response = service(FRI_17).order(competitionId, userId, key, buy(10));

        assertThat(response.tradeId()).isEqualTo(existing.getTradeId());
        assertThat(account.getCash()).isEqualByComparingTo("1000000");
        verify(tradeRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("대회가 끝난 뒤에도 같은 키의 재시도에는 기존 거래로 응답한다")
    void sameKeyAfterCompetitionEndedReturnsExistingTrade() {
        competition = competition(CompetitionStatus.ENDED, "0.000100");
        givenAccount();
        Trade existing = existingTrade(TradeSide.BUY, 10L, todayPrice.getClosePriceId());
        when(tradeRepository.findByAccountAndIdempotencyKey(account, key)).thenReturn(Optional.of(existing));

        CompetitionTradeResponse response = service(FRI_17).order(competitionId, userId, key, buy(10));

        assertThat(response.tradeId()).isEqualTo(existing.getTradeId());
    }

    @Test
    @DisplayName("같은 키로 다른 주문이 오면 거절한다")
    void sameKeyDifferentOrderIsRejected() {
        givenAccount();
        Trade existing = existingTrade(TradeSide.BUY, 10L, todayPrice.getClosePriceId());
        when(tradeRepository.findByAccountAndIdempotencyKey(account, key)).thenReturn(Optional.of(existing));

        assertErrorCode(() -> service(FRI_17).order(competitionId, userId, key, buy(5)),
                ErrorCode.COMPETITION_IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    @DisplayName("진행 중이 아닌 대회에서는 주문할 수 없다")
    void competitionNotActive() {
        competition = competition(CompetitionStatus.READY, "0.000100");
        givenNewOrder();

        assertErrorCode(() -> service(FRI_17).order(competitionId, userId, key, buy(10)),
                ErrorCode.COMPETITION_NOT_ACTIVE);
        verifyNoInteractions(stockRepository, closePriceRepository);
    }

    @Test
    @DisplayName("제외된 참가자는 주문할 수 없다")
    void excludedParticipant() {
        participant = CompetitionParticipant.builder().status(ParticipantStatus.EXCLUDED).build();
        givenNewOrder();

        assertErrorCode(() -> service(FRI_17).order(competitionId, userId, key, buy(10)),
                ErrorCode.COMPETITION_PARTICIPANT_EXCLUDED);
    }

    @Test
    @DisplayName("국내 종목만 주문할 수 있다")
    void usStockNotSupported() {
        givenNewOrder();
        CompetitionStock apple = CompetitionStock.builder().ticker("AAPL").market(Market.US).build();
        when(stockRepository.findByTicker("AAPL")).thenReturn(Optional.of(apple));

        CompetitionTradeRequest request = new CompetitionTradeRequest("AAPL", TradeSide.BUY, 1L, UUID.randomUUID());
        assertErrorCode(() -> service(FRI_17).order(competitionId, userId, key, request),
                ErrorCode.COMPETITION_MARKET_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("없는 종목은 주문할 수 없다")
    void stockNotFound() {
        givenNewOrder();
        when(stockRepository.findByTicker("005930")).thenReturn(Optional.empty());

        assertErrorCode(() -> service(FRI_17).order(competitionId, userId, key, buy(10)),
                ErrorCode.COMPETITION_STOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 가능 시간(16:30~21:00) 밖에는 주문할 수 없다")
    void orderOutsideHours() {
        givenNewOrder();
        when(stockRepository.findByTicker("005930")).thenReturn(Optional.of(samsung));

        assertErrorCode(() -> service(FRI.atTime(16, 29)).order(competitionId, userId, key, buy(10)),
                ErrorCode.COMPETITION_ORDER_CLOSED);
    }

    @Test
    @DisplayName("오늘 종가가 없으면(주말·공휴일) 주문할 수 없다")
    void noTodayPrice() {
        LocalDateTime saturday17 = FRI.plusDays(1).atTime(17, 0);
        givenNewOrder();
        when(stockRepository.findByTicker("005930")).thenReturn(Optional.of(samsung));
        when(closePriceRepository.findFirstByStockAndAvailableAtLessThanEqualOrderByPriceDateDesc(samsung, saturday17))
                .thenReturn(Optional.of(todayPrice)); // 금요일 종가가 최신

        assertErrorCode(() -> service(saturday17).order(competitionId, userId, key, buy(10)),
                ErrorCode.COMPETITION_PRICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("확인한 종가가 최신 종가와 다르면 주문할 수 없다")
    void priceChanged() {
        givenNewOrder();
        givenTodayPrice();

        CompetitionTradeRequest request = new CompetitionTradeRequest("005930", TradeSide.BUY, 10L, UUID.randomUUID());
        assertErrorCode(() -> service(FRI_17).order(competitionId, userId, key, request),
                ErrorCode.PRICE_CHANGED);
    }

    @Test
    @DisplayName("현금이 대금과 수수료보다 적으면 매수할 수 없고 거래를 저장하지 않는다")
    void insufficientCash() {
        account = CompetitionAccount.builder().cash(new BigDecimal("600059.9999")).build();
        givenOrderable();

        assertErrorCode(() -> service(FRI_17).order(competitionId, userId, key, buy(10)),
                ErrorCode.COMPETITION_INSUFFICIENT_CASH);
        verify(tradeRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("보유하지 않은 종목은 매도할 수 없다")
    void sellWithoutHolding() {
        givenOrderable();
        when(holdingRepository.findByAccountAndStock(account, samsung)).thenReturn(Optional.empty());

        assertErrorCode(() -> service(FRI_17).order(competitionId, userId, key, sell(1)),
                ErrorCode.COMPETITION_INSUFFICIENT_HOLDING);
    }

    @Test
    @DisplayName("같은 계좌의 다른 주문과 동시에 저장되면 충돌로 거절한다")
    void optimisticLockConflict() {
        givenOrderable();
        when(holdingRepository.findByAccountAndStock(account, samsung)).thenReturn(Optional.empty());
        when(tradeRepository.saveAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(CompetitionAccount.class, UUID.randomUUID()));

        assertErrorCode(() -> service(FRI_17).order(competitionId, userId, key, buy(10)),
                ErrorCode.COMPETITION_ORDER_CONFLICT);
    }

    @Test
    @DisplayName("같은 키의 주문이 동시에 저장되면 충돌로 거절한다")
    void duplicateKeyConflict() {
        givenOrderable();
        when(holdingRepository.findByAccountAndStock(account, samsung)).thenReturn(Optional.empty());
        when(tradeRepository.saveAndFlush(any())).thenThrow(uniqueViolation("uk_trade_account_idempotency_key"));

        assertErrorCode(() -> service(FRI_17).order(competitionId, userId, key, buy(10)),
                ErrorCode.COMPETITION_ORDER_CONFLICT);
    }

    @Test
    @DisplayName("다른 DB 제약 위반은 그대로 올린다")
    void otherConstraintViolationIsRethrown() {
        givenOrderable();
        when(holdingRepository.findByAccountAndStock(account, samsung)).thenReturn(Optional.empty());
        when(tradeRepository.saveAndFlush(any())).thenThrow(uniqueViolation("ck_trade_quantity"));

        assertThatThrownBy(() -> service(FRI_17).order(competitionId, userId, key, buy(10)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private CompetitionTradeService service(LocalDateTime now) {
        return new CompetitionTradeService(
                competitionRepository, participantRepository, accountRepository, stockRepository,
                closePriceRepository, holdingRepository, tradeRepository,
                Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL));
    }

    private void givenAccount() {
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.of(competition));
        when(participantRepository.findByCompetitionAndUserId(competition, userId)).thenReturn(Optional.of(participant));
        when(accountRepository.findByParticipant(participant)).thenReturn(Optional.of(account));
    }

    private void givenNewOrder() {
        givenAccount();
        when(tradeRepository.findByAccountAndIdempotencyKey(account, key)).thenReturn(Optional.empty());
    }

    private void givenTodayPrice() {
        when(stockRepository.findByTicker("005930")).thenReturn(Optional.of(samsung));
        when(closePriceRepository.findFirstByStockAndAvailableAtLessThanEqualOrderByPriceDateDesc(samsung, FRI_17))
                .thenReturn(Optional.of(todayPrice));
    }

    private void givenOrderable() {
        givenNewOrder();
        givenTodayPrice();
    }

    private void givenTradeSaved() {
        when(tradeRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Trade savedTrade() {
        ArgumentCaptor<Trade> trade = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).saveAndFlush(trade.capture());
        return trade.getValue();
    }

    private CompetitionTradeRequest buy(long quantity) {
        return new CompetitionTradeRequest("005930", TradeSide.BUY, quantity, todayPrice.getClosePriceId());
    }

    private CompetitionTradeRequest sell(long quantity) {
        return new CompetitionTradeRequest("005930", TradeSide.SELL, quantity, todayPrice.getClosePriceId());
    }

    private Trade existingTrade(TradeSide side, long quantity, UUID closePriceId) {
        return Trade.builder()
                .tradeId(UUID.randomUUID())
                .account(account)
                .stock(samsung)
                .closePrice(DailyClosePrice.builder()
                        .closePriceId(closePriceId)
                        .stock(samsung)
                        .priceDate(FRI)
                        .closePrice(new BigDecimal("60000.0000"))
                        .build())
                .side(side)
                .quantity(quantity)
                .price(new BigDecimal("60000.0000"))
                .fee(new BigDecimal("60"))
                .idempotencyKey(key)
                .executedAt(FRI_17)
                .build();
    }

    private static Competition competition(CompetitionStatus status, String feeRate) {
        return Competition.builder().status(status).feeRate(new BigDecimal(feeRate)).build();
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

    private static DataIntegrityViolationException uniqueViolation(String constraintName) {
        return new DataIntegrityViolationException("constraint violation",
                new ConstraintViolationException("constraint violation", new SQLException(), constraintName));
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }
}
