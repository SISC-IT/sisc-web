package org.sejongisc.backend.competition.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

class CompetitionTradeEntityTest {

    @Test
    @DisplayName("국내 종목 주문 시간은 16:30 이상 21:00 미만이다")
    void krOrderHours() {
        assertThat(Market.KR.isOrderOpen(LocalTime.of(16, 29, 59))).isFalse();
        assertThat(Market.KR.isOrderOpen(LocalTime.of(16, 30))).isTrue();
        assertThat(Market.KR.isOrderOpen(LocalTime.of(20, 59, 59))).isTrue();
        assertThat(Market.KR.isOrderOpen(LocalTime.of(21, 0))).isFalse();
    }

    @Test
    @DisplayName("현금을 빼고 넣는다")
    void decreaseAndIncreaseCash() {
        CompetitionAccount account = account("1000000");

        account.decreaseCash(new BigDecimal("600060"));
        account.increaseCash(new BigDecimal("100000"));

        assertThat(account.getCash()).isEqualByComparingTo("499940");
    }

    @Test
    @DisplayName("현금보다 많이 빼면 잔고 부족으로 거절하고 현금은 그대로다")
    void decreaseMoreThanCash() {
        CompetitionAccount account = account("1000000");

        assertThatThrownBy(() -> account.decreaseCash(new BigDecimal("1000000.0001")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_INSUFFICIENT_CASH);
        assertThat(account.getCash()).isEqualByComparingTo("1000000");
    }

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

    private static CompetitionAccount account(String cash) {
        return CompetitionAccount.builder().cash(new BigDecimal(cash)).deposit(BigDecimal.ZERO).build();
    }

    private static Holding holding(long quantity, String avgBuyPrice) {
        return Holding.builder().quantity(quantity).avgBuyPrice(new BigDecimal(avgBuyPrice)).build();
    }
}
