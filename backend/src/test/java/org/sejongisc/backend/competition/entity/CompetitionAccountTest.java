package org.sejongisc.backend.competition.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

class CompetitionAccountTest {

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
    @DisplayName("현금을 예금으로, 예금을 현금으로 옮긴다")
    void depositAndWithdraw() {
        CompetitionAccount account = account("1000000");

        account.depositCash(new BigDecimal("700000"));
        account.withdrawDeposit(new BigDecimal("200000"));

        assertThat(account.getCash()).isEqualByComparingTo("500000");
        assertThat(account.getDeposit()).isEqualByComparingTo("500000");
    }

    @Test
    @DisplayName("현금보다 많이 예금하면 잔고 부족으로 거절하고 현금·예금은 그대로다")
    void depositMoreThanCash() {
        CompetitionAccount account = account("1000000");

        assertThatThrownBy(() -> account.depositCash(new BigDecimal("1000001")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_INSUFFICIENT_CASH);
        assertThat(account.getCash()).isEqualByComparingTo("1000000");
        assertThat(account.getDeposit()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("예금보다 많이 출금하면 예금 부족으로 거절하고 현금·예금은 그대로다")
    void withdrawMoreThanDeposit() {
        CompetitionAccount account = account("1000000");
        account.depositCash(new BigDecimal("300000"));

        assertThatThrownBy(() -> account.withdrawDeposit(new BigDecimal("300000.0001")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_INSUFFICIENT_DEPOSIT);
        assertThat(account.getCash()).isEqualByComparingTo("700000");
        assertThat(account.getDeposit()).isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("이자는 예금에 더한다")
    void addInterest() {
        CompetitionAccount account = account("1000000");
        account.depositCash(new BigDecimal("1000000"));

        account.addInterest(new BigDecimal("82.1917"));

        assertThat(account.getDeposit()).isEqualByComparingTo("1000082.1917");
        assertThat(account.getCash()).isEqualByComparingTo("0");
    }

    private static CompetitionAccount account(String cash) {
        return CompetitionAccount.builder().cash(new BigDecimal(cash)).deposit(BigDecimal.ZERO).build();
    }
}
