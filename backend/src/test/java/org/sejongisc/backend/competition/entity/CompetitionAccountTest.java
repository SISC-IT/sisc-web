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

    private static CompetitionAccount account(String cash) {
        return CompetitionAccount.builder().cash(new BigDecimal(cash)).deposit(BigDecimal.ZERO).build();
    }
}
