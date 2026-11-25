package org.sejongisc.backend.competition.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

class HoldingTest {

    @Test
    @DisplayName("추가 매수하면 수량이 늘고 평균 매입가를 다시 계산한다")
    void buyMore() {
        Holding holding = holding(10L, "50000");

        holding.buy(10L, new BigDecimal("60000"));

        assertThat(holding.getQuantity()).isEqualTo(20L);
        assertThat(holding.getAvgBuyPrice()).isEqualByComparingTo("55000");
    }

    @Test
    @DisplayName("평균 매입가는 소수 4자리에서 반올림한다")
    void buyMoreRoundsAverage() {
        Holding holding = holding(1L, "10000");

        holding.buy(2L, new BigDecimal("10001")); // 30002 / 3 = 10000.6666...

        assertThat(holding.getAvgBuyPrice()).isEqualByComparingTo("10000.6667");
    }

    @Test
    @DisplayName("매도하면 수량만 줄고 평균 매입가는 그대로다")
    void sell() {
        Holding holding = holding(20L, "55000");

        holding.sell(5L);

        assertThat(holding.getQuantity()).isEqualTo(15L);
        assertThat(holding.getAvgBuyPrice()).isEqualByComparingTo("55000");
    }

    @Test
    @DisplayName("보유 수량보다 많이 팔면 거절하고 수량은 그대로다")
    void sellMoreThanHolding() {
        Holding holding = holding(5L, "55000");

        assertThatThrownBy(() -> holding.sell(6L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_INSUFFICIENT_HOLDING);
        assertThat(holding.getQuantity()).isEqualTo(5L);
    }

    private static Holding holding(long quantity, String avgBuyPrice) {
        return Holding.builder().quantity(quantity).avgBuyPrice(new BigDecimal(avgBuyPrice)).build();
    }
}
