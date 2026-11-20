package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Decimals {

    private static final int RATE_SCALE = 6;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Decimals() {
    }

    static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    static BigDecimal percent(BigDecimal change, BigDecimal base) {
        return change.multiply(HUNDRED).divide(base, RATE_SCALE, RoundingMode.HALF_UP);
    }
}
