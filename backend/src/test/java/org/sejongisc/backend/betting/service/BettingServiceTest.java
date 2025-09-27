package org.sejongisc.backend.betting.service;

import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;

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
}
