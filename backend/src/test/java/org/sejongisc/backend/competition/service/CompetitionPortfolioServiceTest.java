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
import org.sejongisc.backend.competition.dto.CompetitionTradeResponse;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.entity.Trade;
import org.sejongisc.backend.competition.entity.TradeSide;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionParticipantRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
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
                tradeRepository, holdingRepository, closePriceRepository, clock);
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
