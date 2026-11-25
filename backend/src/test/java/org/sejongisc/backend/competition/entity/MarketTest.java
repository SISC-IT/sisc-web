package org.sejongisc.backend.competition.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarketTest {

    @Test
    @DisplayName("국내 종목 주문 시간은 16:30 이상 21:00 미만이다")
    void krOrderHours() {
        assertThat(Market.KR.isOrderOpen(LocalTime.of(16, 29, 59))).isFalse();
        assertThat(Market.KR.isOrderOpen(LocalTime.of(16, 30))).isTrue();
        assertThat(Market.KR.isOrderOpen(LocalTime.of(20, 59, 59))).isTrue();
        assertThat(Market.KR.isOrderOpen(LocalTime.of(21, 0))).isFalse();
    }
}
