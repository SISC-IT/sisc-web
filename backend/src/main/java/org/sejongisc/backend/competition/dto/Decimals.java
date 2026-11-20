package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Decimals {

    private static final int RATE_SCALE = 6;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Decimals() {
    }

    static String text(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    static String rateText(BigDecimal rate) {
        return rate == null ? null : rate.toPlainString();
    }

    static BigDecimal percent(BigDecimal change, BigDecimal base) {
        return change.multiply(HUNDRED).divide(base, RATE_SCALE, RoundingMode.HALF_UP);
    }
}
