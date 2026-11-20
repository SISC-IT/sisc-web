package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.competition.dto.UnavailableReason;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Market;

class OrderRuleTest {

    private static final LocalDate FRI = LocalDate.of(2026, 11, 20);
    private static final LocalDateTime FRI_17 = FRI.atTime(17, 0);

    private final Competition active = Competition.builder().status(CompetitionStatus.ACTIVE).build();
    private final CompetitionStock samsung = CompetitionStock.builder().ticker("005930").market(Market.KR).build();
    private final DailyClosePrice fridayPrice = DailyClosePrice.builder()
            .priceDate(FRI).closePrice(new BigDecimal("60000")).build();

    @Test
    @DisplayName("진행 중인 대회, 국내 종목, 주문 시간, 오늘 종가가 모두 맞으면 주문할 수 있다")
    void orderable() {
        assertThat(OrderRule.unavailableReason(active, samsung, fridayPrice, FRI_17)).isNull();
    }

    @Test
    @DisplayName("진행 중이 아닌 대회는 주문할 수 없다")
    void competitionNotActive() {
        Competition ready = Competition.builder().status(CompetitionStatus.READY).build();

        assertThat(OrderRule.unavailableReason(ready, samsung, fridayPrice, FRI_17))
                .isEqualTo(UnavailableReason.COMPETITION_NOT_ACTIVE);
    }

    @Test
    @DisplayName("미국 종목은 아직 주문할 수 없다")
    void usStockNotSupported() {
        CompetitionStock apple = CompetitionStock.builder().ticker("AAPL").market(Market.US).build();

        assertThat(OrderRule.unavailableReason(active, apple, fridayPrice, FRI_17))
                .isEqualTo(UnavailableReason.MARKET_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("주문 가능 시간 밖에는 주문할 수 없다")
    void orderClosed() {
        assertThat(OrderRule.unavailableReason(active, samsung, fridayPrice, FRI.atTime(16, 29)))
                .isEqualTo(UnavailableReason.ORDER_CLOSED);
    }

    @Test
    @DisplayName("최신 종가가 오늘 날짜가 아니면(주말·공휴일) 주문할 수 없다")
    void noTodayPrice() {
        assertThat(OrderRule.unavailableReason(active, samsung, fridayPrice, FRI.plusDays(1).atTime(17, 0)))
                .isEqualTo(UnavailableReason.PRICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("종가가 없으면 주문할 수 없다")
    void noPrice() {
        assertThat(OrderRule.unavailableReason(active, samsung, null, FRI_17))
                .isEqualTo(UnavailableReason.PRICE_UNAVAILABLE);
    }
}
