package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sejongisc.backend.assetmanagement.model.ListedStock;
import org.sejongisc.backend.assetmanagement.service.MarketDataClient;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.repository.CompetitionStockRepository;
import org.sejongisc.backend.competition.service.CompetitionStockSyncService.SyncResult;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompetitionStockSyncServiceTest {

    private static final String KOSPI = "0";
    private static final String KOSDAQ = "10";

    @Mock
    private MarketDataClient marketDataClient;

    @Mock
    private CompetitionStockRepository stockRepository;

    @Captor
    private ArgumentCaptor<List<CompetitionStock>> saved;

    private CompetitionStockSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new CompetitionStockSyncService(
                marketDataClient, stockRepository, List.of(KOSPI, KOSDAQ));
        when(marketDataClient.fetchDomesticListedStocks(any())).thenReturn(List.of());
        when(stockRepository.findByMarket(Market.KR)).thenReturn(List.of());
    }

    private CompetitionStock existing(String ticker, String name) {
        return CompetitionStock.builder()
                .stockId(UUID.randomUUID())
                .ticker(ticker)
                .name(name)
                .market(Market.KR)
                .build();
    }

    private void listed(String marketType, ListedStock... stocks) {
        when(marketDataClient.fetchDomesticListedStocks(marketType)).thenReturn(List.of(stocks));
    }

    @Test
    @DisplayName("새 종목을 국내 종목으로 추가한다")
    void addsNewStocks() {
        listed(KOSPI, new ListedStock("005930", "삼성전자"));
        listed(KOSDAQ, new ListedStock("247540", "에코프로비엠"));

        SyncResult result = syncService.syncDomestic();

        verify(stockRepository).saveAll(saved.capture());
        assertThat(saved.getValue())
                .extracting(CompetitionStock::getTicker, CompetitionStock::getName, CompetitionStock::getMarket)
                .containsExactly(
                        tuple("005930", "삼성전자", Market.KR),
                        tuple("247540", "에코프로비엠", Market.KR));
        assertThat(result).isEqualTo(new SyncResult(2, 0, 0));
    }

    @Test
    @DisplayName("이름이 바뀐 종목은 이름만 갱신한다")
    void renamesChangedStock() {
        CompetitionStock samsung = existing("005930", "삼성전자");
        when(stockRepository.findByMarket(Market.KR)).thenReturn(List.of(samsung));
        listed(KOSPI, new ListedStock("005930", "삼성전자우"));

        SyncResult result = syncService.syncDomestic();

        verify(stockRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).containsExactly(samsung);
        assertThat(samsung.getName()).isEqualTo("삼성전자우");
        assertThat(result).isEqualTo(new SyncResult(0, 1, 0));
    }

    @Test
    @DisplayName("변동이 없으면 저장하지 않는다")
    void leavesUnchangedStockAlone() {
        when(stockRepository.findByMarket(Market.KR)).thenReturn(List.of(existing("005930", "삼성전자")));
        listed(KOSPI, new ListedStock("005930", "삼성전자"));

        SyncResult result = syncService.syncDomestic();

        verify(stockRepository, never()).saveAll(anyList());
        assertThat(result).isEqualTo(new SyncResult(0, 0, 1));
    }

    @Test
    @DisplayName("목록에서 사라진 종목은 그대로 둔다")
    void keepsStocksMissingFromList() {
        when(stockRepository.findByMarket(Market.KR)).thenReturn(List.of(existing("000001", "상장폐지")));
        listed(KOSPI, new ListedStock("005930", "삼성전자"));

        syncService.syncDomestic();

        verify(stockRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(CompetitionStock::getTicker).containsExactly("005930");
        verify(stockRepository, never()).delete(any());
        verify(stockRepository, never()).deleteAll(anyList());
        verify(stockRepository, never()).deleteAll();
    }

    @Test
    @DisplayName("앞뒤 공백만 다른 값은 변경으로 보지 않는다")
    void trimsCodeAndName() {
        when(stockRepository.findByMarket(Market.KR)).thenReturn(List.of(existing("005930", "삼성전자")));
        listed(KOSPI, new ListedStock(" 005930 ", " 삼성전자 "));

        SyncResult result = syncService.syncDomestic();

        verify(stockRepository, never()).saveAll(anyList());
        assertThat(result).isEqualTo(new SyncResult(0, 0, 1));
    }

    @Test
    @DisplayName("빈 값이나 길이를 넘는 항목은 버린다")
    void skipsInvalidItems() {
        listed(KOSPI,
                new ListedStock(" ", "코드없음"),
                new ListedStock("000001", null),
                new ListedStock("1".repeat(21), "코드초과"),
                new ListedStock("000002", "가".repeat(101)),
                new ListedStock("005930", "삼성전자"));

        SyncResult result = syncService.syncDomestic();

        verify(stockRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(CompetitionStock::getTicker).containsExactly("005930");
        assertThat(result).isEqualTo(new SyncResult(1, 0, 0));
    }

    @Test
    @DisplayName("같은 종목코드가 여러 번 오면 먼저 온 것을 쓴다")
    void usesFirstOfDuplicateCodes() {
        listed(KOSPI, new ListedStock("005930", "삼성전자"));
        listed(KOSDAQ, new ListedStock("005930", "다른이름"));

        syncService.syncDomestic();

        verify(stockRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(CompetitionStock::getName).containsExactly("삼성전자");
    }

    @Test
    @DisplayName("한 시장 조회가 실패해도 다른 시장은 반영한다")
    void continuesWhenOneMarketFails() {
        when(marketDataClient.fetchDomesticListedStocks(KOSPI))
                .thenThrow(new CustomException(ErrorCode.KIWOOM_CHART_API_FAILED));
        listed(KOSDAQ, new ListedStock("247540", "에코프로비엠"));

        SyncResult result = syncService.syncDomestic();

        verify(stockRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(CompetitionStock::getTicker).containsExactly("247540");
        assertThat(result).isEqualTo(new SyncResult(1, 0, 0));
    }

    @Test
    @DisplayName("모든 시장 조회가 실패하면 아무것도 저장하지 않는다")
    void savesNothingWhenAllMarketsFail() {
        when(marketDataClient.fetchDomesticListedStocks(any()))
                .thenThrow(new CustomException(ErrorCode.KIWOOM_CHART_API_FAILED));
        when(stockRepository.findByMarket(Market.KR))
                .thenReturn(new ArrayList<>(List.of(existing("005930", "삼성전자"))));

        SyncResult result = syncService.syncDomestic();

        verify(stockRepository, never()).saveAll(anyList());
        assertThat(result).isEqualTo(new SyncResult(0, 0, 0));
    }

    @Test
    @DisplayName("받은 목록이 비어 있으면 아무것도 저장하지 않는다")
    void savesNothingWhenListsAreEmpty() {
        SyncResult result = syncService.syncDomestic();

        verify(stockRepository, never()).saveAll(anyList());
        assertThat(result).isEqualTo(new SyncResult(0, 0, 0));
    }
}
