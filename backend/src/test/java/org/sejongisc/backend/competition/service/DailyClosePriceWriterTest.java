package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.assetmanagement.model.DailyCandle;
import org.sejongisc.backend.assetmanagement.service.MarketDataClient;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.repository.DailyClosePriceRepository;

@ExtendWith(MockitoExtension.class)
class DailyClosePriceWriterTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 11, 11);
    private static final LocalTime AVAILABLE_AT = LocalTime.of(16, 30);

    @Mock
    private MarketDataClient marketDataClient;

    @Mock
    private DailyClosePriceRepository closePriceRepository;

    private DailyClosePriceWriter writer;
    private CompetitionStock samsung;

    @BeforeEach
    void setUp() {
        writer = new DailyClosePriceWriter(marketDataClient, closePriceRepository, AVAILABLE_AT);
        samsung = CompetitionStock.builder()
                .stockId(UUID.randomUUID())
                .ticker("005930")
                .name("삼성전자")
                .market(Market.KR)
                .build();
    }

    private DailyCandle candle(LocalDate date, String closePrice) {
        return DailyCandle.builder()
                .ticker("005930")
                .date(date)
                .closePrice(new BigDecimal(closePrice))
                .currency("KRW")
                .build();
    }

    @Test
    @DisplayName("기준일 종가를 저장하고 거래 가능 시각을 16:30으로 부여한다")
    void savesClosePriceWithAvailableAt() {
        when(closePriceRepository.existsByStockAndPriceDate(samsung, BASE_DATE)).thenReturn(false);
        when(marketDataClient.fetchDomesticDailyCandles("005930", BASE_DATE))
                .thenReturn(List.of(candle(BASE_DATE, "61000")));

        boolean saved = writer.saveIfAbsent(samsung, BASE_DATE);

        assertThat(saved).isTrue();
        ArgumentCaptor<DailyClosePrice> captor = ArgumentCaptor.forClass(DailyClosePrice.class);
        verify(closePriceRepository).save(captor.capture());

        DailyClosePrice price = captor.getValue();
        assertThat(price.getStock()).isEqualTo(samsung);
        assertThat(price.getPriceDate()).isEqualTo(BASE_DATE);
        assertThat(price.getClosePrice()).isEqualByComparingTo("61000");
        assertThat(price.getAvailableAt()).isEqualTo(LocalDateTime.of(BASE_DATE, AVAILABLE_AT));
    }

    @Test
    @DisplayName("이미 적재된 종가는 다시 저장하지 않는다")
    void doesNotSaveWhenAlreadyExists() {
        when(closePriceRepository.existsByStockAndPriceDate(samsung, BASE_DATE)).thenReturn(true);

        boolean saved = writer.saveIfAbsent(samsung, BASE_DATE);

        assertThat(saved).isFalse();
        // 중복이면 키움을 호출하지 않는다.
        verify(marketDataClient, never()).fetchDomesticDailyCandles(any(), any());
        verify(closePriceRepository, never()).save(any());
    }

    @Test
    @DisplayName("휴장일이면 기준일 캔들이 없어 저장하지 않는다")
    void doesNotSaveOnMarketHoliday() {
        when(closePriceRepository.existsByStockAndPriceDate(samsung, BASE_DATE)).thenReturn(false);
        // 키움은 기준일부터 과거 방향으로 주므로, 휴장일이면 직전 거래일 캔들만 온다.
        when(marketDataClient.fetchDomesticDailyCandles("005930", BASE_DATE))
                .thenReturn(List.of(candle(BASE_DATE.minusDays(1), "60000")));

        boolean saved = writer.saveIfAbsent(samsung, BASE_DATE);

        assertThat(saved).isFalse();
        verify(closePriceRepository, never()).save(any());
    }

    @Test
    @DisplayName("응답이 비어 있으면 저장하지 않는다")
    void doesNotSaveWhenNoCandles() {
        when(closePriceRepository.existsByStockAndPriceDate(samsung, BASE_DATE)).thenReturn(false);
        when(marketDataClient.fetchDomesticDailyCandles("005930", BASE_DATE))
                .thenReturn(List.of());

        boolean saved = writer.saveIfAbsent(samsung, BASE_DATE);

        assertThat(saved).isFalse();
        verify(closePriceRepository, never()).save(any());
    }

    @Test
    @DisplayName("여러 날짜가 와도 기준일 종가만 저장한다")
    void savesOnlyBaseDateCandle() {
        when(closePriceRepository.existsByStockAndPriceDate(samsung, BASE_DATE)).thenReturn(false);
        when(marketDataClient.fetchDomesticDailyCandles("005930", BASE_DATE))
                .thenReturn(List.of(
                        candle(BASE_DATE.minusDays(2), "59000"),
                        candle(BASE_DATE.minusDays(1), "60000"),
                        candle(BASE_DATE, "61000")));

        writer.saveIfAbsent(samsung, BASE_DATE);

        ArgumentCaptor<DailyClosePrice> captor = ArgumentCaptor.forClass(DailyClosePrice.class);
        verify(closePriceRepository).save(captor.capture());
        assertThat(captor.getValue().getClosePrice()).isEqualByComparingTo("61000");
    }

    @Test
    @DisplayName("키움 호출이 실패하면 예외가 그대로 올라간다")
    void propagatesMarketDataFailure() {
        when(closePriceRepository.existsByStockAndPriceDate(samsung, BASE_DATE)).thenReturn(false);
        when(marketDataClient.fetchDomesticDailyCandles("005930", BASE_DATE))
                .thenThrow(new IllegalStateException("키움 오류"));

        assertThatThrownBy(() -> writer.saveIfAbsent(samsung, BASE_DATE))
                .isInstanceOf(IllegalStateException.class);
        verify(closePriceRepository, never()).save(any());
    }
}
