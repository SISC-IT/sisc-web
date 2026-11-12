package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.repository.CompetitionStockRepository;
import org.sejongisc.backend.competition.service.DailyClosePriceCollector.CollectionResult;

@ExtendWith(MockitoExtension.class)
class DailyClosePriceCollectorTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 11, 11);

    @Mock
    private DailyClosePriceWriter writer;

    @Mock
    private CompetitionStockRepository stockRepository;

    private DailyClosePriceCollector collector;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(
                BASE_DATE.atTime(16, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul"));
        collector = new DailyClosePriceCollector(writer, stockRepository, fixed);
    }

    private CompetitionStock stock(String ticker) {
        return CompetitionStock.builder()
                .stockId(UUID.randomUUID())
                .ticker(ticker)
                .name(ticker + " 종목")
                .market(Market.KR)
                .build();
    }

    @Test
    @DisplayName("국내 종목 전체를 수집한다")
    void collectsAllDomesticStocks() {
        CompetitionStock samsung = stock("005930");
        CompetitionStock hynix = stock("000660");
        when(stockRepository.findByMarket(Market.KR)).thenReturn(List.of(samsung, hynix));
        when(writer.saveIfAbsent(any(), any())).thenReturn(true);

        CollectionResult result = collector.collectDomestic();

        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
        verify(writer).saveIfAbsent(samsung, BASE_DATE);
        verify(writer).saveIfAbsent(hynix, BASE_DATE);
    }

    @Test
    @DisplayName("한 종목이 실패해도 나머지는 계속 수집한다")
    void keepsCollectingWhenOneStockFails() {
        CompetitionStock failing = stock("005930");
        CompetitionStock ok = stock("000660");
        when(stockRepository.findByMarket(Market.KR)).thenReturn(List.of(failing, ok));
        when(writer.saveIfAbsent(failing, BASE_DATE)).thenThrow(new IllegalStateException("키움 오류"));
        when(writer.saveIfAbsent(ok, BASE_DATE)).thenReturn(true);

        CollectionResult result = collector.collectDomestic();

        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        // 실패한 종목 뒤에 있는 종목도 호출된다.
        verify(writer).saveIfAbsent(ok, BASE_DATE);
    }

    @Test
    @DisplayName("이미 적재된 종목은 건너뛴 것으로 집계한다")
    void countsAlreadySavedAsSkipped() {
        CompetitionStock samsung = stock("005930");
        when(stockRepository.findByMarket(Market.KR)).thenReturn(List.of(samsung));
        when(writer.saveIfAbsent(samsung, BASE_DATE)).thenReturn(false);

        CollectionResult result = collector.collectDomestic();

        assertThat(result.saved()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    @DisplayName("수집 대상 종목이 없으면 키움을 호출하지 않는다")
    void doesNotCallMarketDataWhenNoStocks() {
        when(stockRepository.findByMarket(Market.KR)).thenReturn(List.of());

        CollectionResult result = collector.collectDomestic();

        assertThat(result.saved()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
        verify(writer, never()).saveIfAbsent(any(), any());
    }

    @Test
    @DisplayName("미국 종목은 수집하지 않는다")
    void collectsDomesticMarketOnly() {
        when(stockRepository.findByMarket(Market.KR)).thenReturn(List.of());

        collector.collectDomestic();

        verify(stockRepository).findByMarket(Market.KR);
        verify(stockRepository, never()).findByMarket(Market.US);
    }
}
