package org.sejongisc.backend.competition.entity;

import java.time.LocalTime;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum Market {
    // 주문 시간은 실제 장 시간이 아니라 종가 확정 후 주문을 받는 시간이다.
    KR("KRW", LocalTime.of(16, 30), LocalTime.of(21, 0)),
    US("USD", LocalTime.of(9, 0), LocalTime.of(21, 0));

    private final String currency;
    private final LocalTime orderOpenTime;   // 이 시각부터 주문 가능
    private final LocalTime orderCloseTime;  // 이 시각부터 주문 불가

    public boolean isOrderOpen(LocalTime time) {
        return !time.isBefore(orderOpenTime) && time.isBefore(orderCloseTime);
    }

    public static Market from(String value) {
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(market -> market.name().equalsIgnoreCase(trimmed))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_COMPETITION_MARKET));
    }
}
