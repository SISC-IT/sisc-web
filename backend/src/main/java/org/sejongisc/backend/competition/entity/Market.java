package org.sejongisc.backend.competition.entity;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum Market {
    KR("KRW"),
    US("USD");

    private final String currency;

    public static Market from(String value) {
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(market -> market.name().equalsIgnoreCase(trimmed))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_COMPETITION_MARKET));
    }
}
