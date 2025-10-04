package org.sejongisc.backend.betting.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sejongisc.backend.betting.entity.BetRound;
import org.sejongisc.backend.betting.entity.Scope;
import org.sejongisc.backend.betting.entity.Stock;
import org.sejongisc.backend.betting.entity.MarketType;
import org.sejongisc.backend.betting.repository.BetRoundRepository;
import org.sejongisc.backend.betting.repository.StockRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BettingServiceTest {

    private BetRoundRepository betRoundRepository;
    private StockRepository stockRepository;
    private BettingService bettingService;

    @BeforeEach
    void setUp() {
        betRoundRepository = mock(BetRoundRepository.class);
        stockRepository = mock(StockRepository.class);
        bettingService = new BettingService(betRoundRepository, stockRepository);
    }

    @Test
    void getStock_빈리스트면_예외발생() {
        when(stockRepository.findAll()).thenReturn(List.of());

        CustomException ex = assertThrows(CustomException.class,
                () -> bettingService.getStock());

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STOCK_NOT_FOUND);
        verify(stockRepository, times(1)).findAll();
    }

    @Test
    void getStock_리스트있으면_하나반환() {
        Stock s1 = Stock.builder()
                .name("삼성전자")
                .symbol("005930")
                .market(MarketType.KOREA)
                .previousClosePrice(BigDecimal.valueOf(85400))
                .build();

        Stock s2 = Stock.builder()
                .name("애플")
                .symbol("AAPL")
                .market(MarketType.US)
                .previousClosePrice(BigDecimal.valueOf(251.65))
                .build();

        when(stockRepository.findAll()).thenReturn(List.of(s1, s2));

        Stock chosen = bettingService.getStock();

        assertThat(List.of(s1, s2)).contains(chosen); // 둘 중 하나여야 함
        verify(stockRepository, times(1)).findAll();
    }

    @Test
    void setAllowFree_항상_불린값반환() {
        boolean result = bettingService.setAllowFree();
        assertThat(result).isInstanceOf(Boolean.class);
    }

    @Test
    void createBetRound_DAILY_정상저장() {
        Stock stock = Stock.builder()
                .name("삼성전자")
                .symbol("005930")
                .market(MarketType.KOREA)
                .previousClosePrice(BigDecimal.valueOf(85400))
                .build();

        when(stockRepository.findAll()).thenReturn(List.of(stock));

        bettingService.createBetRound(Scope.DAILY);

        ArgumentCaptor<BetRound> captor = ArgumentCaptor.forClass(BetRound.class);
        verify(betRoundRepository, times(1)).save(captor.capture());

        BetRound saved = captor.getValue();
        assertThat(saved.getScope()).isEqualTo(Scope.DAILY);
        assertThat(saved.getSymbol()).isEqualTo("005930");
        assertThat(saved.getTitle()).contains("삼성전자");
    }

    @Test
    @DisplayName("활성화된 DAILY BetRound 조회 성공")
    void findActiveRound_Success() {
        // given
        BetRound dailyRound = BetRound.builder()
                .betRoundID(UUID.randomUUID())
                .scope(Scope.DAILY)
                .status(true)
                .title("Daily Round")
                .openAt(LocalDateTime.now())
                .build();

        when(betRoundRepository.findByStatusTrueAndScope(Scope.DAILY))
                .thenReturn(Optional.of(dailyRound));

        // when
        Optional<BetRound> result = bettingService.getActiveRound(Scope.DAILY);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getScope()).isEqualTo(Scope.DAILY);
        assertThat(result.get().isStatus()).isTrue();
        verify(betRoundRepository, times(1)).findByStatusTrueAndScope(Scope.DAILY);
    }

    @Test
    @DisplayName("활성화된 BetRound가 없을 때 빈 Optional 반환")
    void findActiveRound_NotFound() {
        // given
        when(betRoundRepository.findByStatusTrueAndScope(Scope.DAILY))
                .thenReturn(Optional.empty());

        // when
        Optional<BetRound> result = bettingService.getActiveRound(Scope.DAILY);

        // then
        assertThat(result).isEmpty();
        verify(betRoundRepository, times(1)).findByStatusTrueAndScope(Scope.DAILY);
    }

    @Test
    @DisplayName("모든 BetRound 최신순 조회 성공")
    void getAllBetRounds_Success() {
        // given
        List<BetRound> betRounds = List.of(
                BetRound.builder()
                        .betRoundID(UUID.randomUUID())
                        .scope(Scope.DAILY)
                        .status(true)
                        .title("Daily Round")
                        .openAt(LocalDateTime.now())
                        .build(),
                BetRound.builder()
                        .betRoundID(UUID.randomUUID())
                        .scope(Scope.WEEKLY)
                        .status(false)
                        .title("Weekly Round")
                        .openAt(LocalDateTime.now().minusDays(1))
                        .build()
        );

        when(betRoundRepository.findAllByOrderBySettleAtDesc())
                .thenReturn(betRounds);

        // when
        List<BetRound> results = bettingService.getAllBetRounds();

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getScope()).isEqualTo(Scope.DAILY);
        assertThat(results.get(1).getScope()).isEqualTo(Scope.WEEKLY);
        verify(betRoundRepository, times(1)).findAllByOrderBySettleAtDesc();
    }

    @Test
    @DisplayName("BetRound가 없을 때 빈 리스트 반환")
    void getAllBetRounds_Empty() {
        // given
        when(betRoundRepository.findAllByOrderBySettleAtDesc())
                .thenReturn(Collections.emptyList());

        // when
        List<BetRound> results = bettingService.getAllBetRounds();

        // then
        assertThat(results).isEmpty();
        verify(betRoundRepository, times(1)).findAllByOrderBySettleAtDesc();
    }

    @Test
    @DisplayName("WEEKLY BetRound 조회 성공")
    void findActiveRound_Weekly_Success() {
        // given
        BetRound weeklyRound = BetRound.builder()
                .betRoundID(UUID.randomUUID())
                .scope(Scope.WEEKLY)
                .status(true)
                .title("Weekly Round")
                .openAt(LocalDateTime.now())
                .build();

        when(betRoundRepository.findByStatusTrueAndScope(Scope.WEEKLY))
                .thenReturn(Optional.of(weeklyRound));

        // when
        Optional<BetRound> result = bettingService.getActiveRound(Scope.WEEKLY);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getScope()).isEqualTo(Scope.WEEKLY);
        verify(betRoundRepository, times(1)).findByStatusTrueAndScope(Scope.WEEKLY);
    }
}