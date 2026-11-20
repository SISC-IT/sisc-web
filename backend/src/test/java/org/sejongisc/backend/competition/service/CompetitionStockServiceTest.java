package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
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
import org.sejongisc.backend.competition.dto.CompetitionStockDetailResponse;
import org.sejongisc.backend.competition.dto.CompetitionStockListResponse;
import org.sejongisc.backend.competition.dto.CompetitionStockSummary;
import org.sejongisc.backend.competition.dto.UnavailableReason;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionParticipantRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.competition.repository.CompetitionStockRepository;
import org.sejongisc.backend.competition.repository.DailyClosePriceRepository;
import org.sejongisc.backend.competition.repository.HoldingRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CompetitionStockServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate FRI = LocalDate.of(2026, 11, 6);
    private static final LocalDate MON = LocalDate.of(2026, 11, 9);
    private static final LocalDateTime NOW = MON.atTime(17, 0);
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20, Sort.by("ticker"));

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private CompetitionStockRepository stockRepository;

    @Mock
    private DailyClosePriceRepository closePriceRepository;

    @Mock
    private CompetitionParticipantRepository participantRepository;

    @Mock
    private CompetitionAccountRepository accountRepository;

    @Mock
    private HoldingRepository holdingRepository;

    private CompetitionStockService stockService;

    private final UUID competitionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Competition competition;
    private CompetitionStock samsung;
    private CompetitionParticipant participant;
    private CompetitionAccount account;

    @BeforeEach
    void setUp() {
        stockService = new CompetitionStockService(
                competitionRepository, stockRepository, closePriceRepository,
                participantRepository, accountRepository, holdingRepository,
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
        competition = Competition.builder()
                .competitionId(competitionId)
                .feeRate(new BigDecimal("0.000100"))
                .build();
        samsung = CompetitionStock.builder()
                .stockId(UUID.randomUUID())
                .ticker("005930")
                .name("삼성전자")
                .market(Market.KR)
                .build();
        participant = CompetitionParticipant.builder().participantId(UUID.randomUUID()).build();
        account = CompetitionAccount.builder().cash(new BigDecimal("6999900.0000")).build();
    }

    @Test
    @DisplayName("종가와 직전 거래일 종가로 등락률을 계산해 목록을 반환한다")
    void searchReturnsClosePriceAndChangeRate() {
        givenCompetition();
        when(stockRepository.search(Market.KR, "%삼성%", FIRST_PAGE))
                .thenReturn(new PageImpl<>(List.of(samsung), FIRST_PAGE, 1));
        DailyClosePrice latest = price(MON, "60000.0000");
        givenListPrices(latest, price(FRI, "59000.0000"));

        CompetitionStockListResponse response = stockService.search(competitionId, "kr", "삼성", 0, 20);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.exchangeRate()).isNull();
        CompetitionStockSummary stock = response.stocks().get(0);
        assertThat(stock.ticker()).isEqualTo("005930");
        assertThat(stock.companyName()).isEqualTo("삼성전자");
        assertThat(stock.currency()).isEqualTo("KRW");
        assertThat(stock.closePriceId()).isEqualTo(latest.getClosePriceId());
        assertThat(stock.closePrice()).isEqualByComparingTo("60000");
        assertThat(stock.closePriceKrw()).isEqualByComparingTo("60000");
        assertThat(stock.previousClosePrice()).isEqualByComparingTo("59000");
        assertThat(stock.dailyChangeRate()).isEqualByComparingTo("1.694915");
        assertThat(stock.priceDate()).isEqualTo(MON);
        assertThat(stock.tradable()).isTrue();
        assertThat(stock.unavailableReason()).isNull();
    }

    @Test
    @DisplayName("종가가 없는 종목은 가격이 null이고 거래할 수 없다")
    void searchMarksStockWithoutPriceUntradable() {
        givenCompetition();
        when(stockRepository.search(null, "%%", FIRST_PAGE))
                .thenReturn(new PageImpl<>(List.of(samsung), FIRST_PAGE, 1));
        givenListPrices();

        CompetitionStockSummary stock = stockService.search(competitionId, null, null, 0, 20).stocks().get(0);

        assertThat(stock.closePriceId()).isNull();
        assertThat(stock.closePrice()).isNull();
        assertThat(stock.closePriceKrw()).isNull();
        assertThat(stock.priceDate()).isNull();
        assertThat(stock.tradable()).isFalse();
        assertThat(stock.unavailableReason()).isEqualTo(UnavailableReason.PRICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("종가가 1건이면 등락률 없이 거래 가능으로 반환한다")
    void searchOmitsChangeRateWithoutPreviousPrice() {
        givenCompetition();
        when(stockRepository.search(null, "%%", FIRST_PAGE))
                .thenReturn(new PageImpl<>(List.of(samsung), FIRST_PAGE, 1));
        givenListPrices(price(MON, "60000.0000"));

        CompetitionStockSummary stock = stockService.search(competitionId, null, null, 0, 20).stocks().get(0);

        assertThat(stock.closePrice()).isEqualByComparingTo("60000");
        assertThat(stock.previousClosePrice()).isNull();
        assertThat(stock.dailyChangeRate()).isNull();
        assertThat(stock.tradable()).isTrue();
    }

    @Test
    @DisplayName("페이지와 크기를 범위 안으로 맞추고 검색어는 소문자로 바꾼다")
    void searchNormalizesPageAndQuery() {
        givenCompetition();
        Pageable expected = PageRequest.of(0, 100, Sort.by("ticker"));
        when(stockRepository.search(null, "%aapl%", expected)).thenReturn(new PageImpl<>(List.of(), expected, 0));

        CompetitionStockListResponse response = stockService.search(competitionId, null, " AAPL ", -1, 500);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(100);
        assertThat(response.stocks()).isEmpty();
        verifyNoInteractions(closePriceRepository);
    }

    @Test
    @DisplayName("검색어의 %와 _는 와일드카드가 아닌 글자로 검색한다")
    void searchEscapesLikeWildcards() {
        givenCompetition();
        when(stockRepository.search(null, "%50!%!_a!!%", FIRST_PAGE)).thenReturn(new PageImpl<>(List.of(), FIRST_PAGE, 0));

        stockService.search(competitionId, null, "50%_A!", 0, 20);

        verify(stockRepository).search(null, "%50!%!_a!!%", FIRST_PAGE);
    }

    @Test
    @DisplayName("서버 로캘이 터키어여도 검색어를 같은 소문자로 바꾼다")
    void searchLowercasesRegardlessOfLocale() {
        givenCompetition();
        when(stockRepository.search(null, "%ibm%", FIRST_PAGE)).thenReturn(new PageImpl<>(List.of(), FIRST_PAGE, 0));
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            stockService.search(competitionId, null, "IBM", 0, 20);
        } finally {
            Locale.setDefault(original);
        }

        verify(stockRepository).search(null, "%ibm%", FIRST_PAGE);
    }

    @Test
    @DisplayName("지원하지 않는 시장 구분은 거부한다")
    void searchRejectsUnknownMarket() {
        givenCompetition();

        assertThatThrownBy(() -> stockService.search(competitionId, "JP", null, 0, 20))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_COMPETITION_MARKET);
        verifyNoInteractions(stockRepository);
    }

    @Test
    @DisplayName("시장 구분이 빈 문자열이면 전체 시장을 조회한다")
    void searchTreatsBlankMarketAsAll() {
        givenCompetition();
        when(stockRepository.search(null, "%%", FIRST_PAGE)).thenReturn(new PageImpl<>(List.of(), FIRST_PAGE, 0));

        stockService.search(competitionId, " ", null, 0, 20);

        verify(stockRepository).search(null, "%%", FIRST_PAGE);
    }

    @Test
    @DisplayName("없는 대회의 종목 목록은 조회할 수 없다")
    void searchRejectsUnknownCompetition() {
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.search(competitionId, null, null, 0, 20))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_NOT_FOUND);
    }

    @Test
    @DisplayName("보유 종목 상세는 평가액과 손익, 수익률을 함께 반환한다")
    void detailReturnsHoldingValuation() {
        givenParticipant();
        givenPrices(price(MON, "60000.0000"), price(FRI, "59000.0000"));
        when(holdingRepository.findByAccountAndStock(account, samsung))
                .thenReturn(Optional.of(holding(10, "58000.0000")));

        CompetitionStockDetailResponse response = stockService.getDetail(competitionId, "005930", userId);

        assertThat(response.closePrice()).isEqualByComparingTo("60000");
        assertThat(response.closePriceKrw()).isEqualByComparingTo("60000");
        assertThat(response.exchangeRate()).isNull();
        assertThat(response.dailyChangeRate()).isEqualByComparingTo("1.694915");
        assertThat(response.holdingQuantity()).isEqualTo(10);
        assertThat(response.averageBuyPrice()).isEqualByComparingTo("58000");
        assertThat(response.averageBuyPriceKrw()).isEqualByComparingTo("58000");
        assertThat(response.investedAmountKrw()).isEqualByComparingTo("580000");
        assertThat(response.valuationAmountKrw()).isEqualByComparingTo("600000");
        assertThat(response.profitLossKrw()).isEqualByComparingTo("20000");
        assertThat(response.holdingReturnRate()).isEqualByComparingTo("3.448276");
        assertThat(response.cashBalance()).isEqualByComparingTo("6999900");
        assertThat(response.tradingFeeRate()).isEqualByComparingTo("0.0001");
        assertThat(response.tradable()).isTrue();
    }

    @Test
    @DisplayName("보유하지 않은 종목 상세는 수량 0과 현금 잔고만 반환한다")
    void detailWithoutHolding() {
        givenParticipant();
        givenPrices(price(MON, "60000.0000"));
        when(holdingRepository.findByAccountAndStock(account, samsung)).thenReturn(Optional.empty());

        CompetitionStockDetailResponse response = stockService.getDetail(competitionId, "005930", userId);

        assertThat(response.holdingQuantity()).isZero();
        assertThat(response.averageBuyPrice()).isNull();
        assertThat(response.investedAmountKrw()).isNull();
        assertThat(response.valuationAmountKrw()).isNull();
        assertThat(response.profitLossKrw()).isNull();
        assertThat(response.holdingReturnRate()).isNull();
        assertThat(response.cashBalance()).isEqualByComparingTo("6999900");
    }

    @Test
    @DisplayName("종가가 없으면 보유 종목이어도 평가액을 계산하지 않는다")
    void detailWithoutPrice() {
        givenParticipant();
        givenPrices();
        when(holdingRepository.findByAccountAndStock(account, samsung))
                .thenReturn(Optional.of(holding(10, "58000.0000")));

        CompetitionStockDetailResponse response = stockService.getDetail(competitionId, "005930", userId);

        assertThat(response.investedAmountKrw()).isEqualByComparingTo("580000");
        assertThat(response.valuationAmountKrw()).isNull();
        assertThat(response.profitLossKrw()).isNull();
        assertThat(response.holdingReturnRate()).isNull();
        assertThat(response.tradable()).isFalse();
        assertThat(response.unavailableReason()).isEqualTo(UnavailableReason.PRICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("없는 종목은 상세를 조회할 수 없다")
    void detailRejectsUnknownStock() {
        givenCompetition();
        when(stockRepository.findByTicker("999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.getDetail(competitionId, "999999", userId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_STOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("참가자가 아니면 상세를 조회할 수 없다")
    void detailRejectsNonParticipant() {
        givenCompetition();
        when(stockRepository.findByTicker("005930")).thenReturn(Optional.of(samsung));
        when(participantRepository.findByCompetitionAndUserId(competition, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.getDetail(competitionId, "005930", userId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND);
    }

    private void givenCompetition() {
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.of(competition));
    }

    private void givenParticipant() {
        givenCompetition();
        when(stockRepository.findByTicker("005930")).thenReturn(Optional.of(samsung));
        when(participantRepository.findByCompetitionAndUserId(competition, userId))
                .thenReturn(Optional.of(participant));
        when(accountRepository.findByParticipant(participant)).thenReturn(Optional.of(account));
    }

    private void givenListPrices(DailyClosePrice... prices) {
        when(closePriceRepository.findLatestTwoByStockIds(List.of(samsung.getStockId()), NOW))
                .thenReturn(List.of(prices));
    }

    private void givenPrices(DailyClosePrice... prices) {
        when(closePriceRepository.findTop2ByStockAndAvailableAtLessThanEqualOrderByPriceDateDesc(samsung, NOW))
                .thenReturn(List.of(prices));
    }

    private DailyClosePrice price(LocalDate priceDate, String closePrice) {
        return DailyClosePrice.builder()
                .closePriceId(UUID.randomUUID())
                .stock(samsung)
                .priceDate(priceDate)
                .closePrice(new BigDecimal(closePrice))
                .build();
    }

    private Holding holding(long quantity, String avgBuyPrice) {
        return Holding.builder()
                .account(account)
                .stock(samsung)
                .quantity(quantity)
                .avgBuyPrice(new BigDecimal(avgBuyPrice))
                .build();
    }
}
