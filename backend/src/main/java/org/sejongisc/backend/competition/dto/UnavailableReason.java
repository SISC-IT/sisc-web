package org.sejongisc.backend.competition.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.ErrorCode;

// 거래할 수 없는 이유. 조회 API는 이 값을 내려주고, 주문 API는 짝지어진 에러 코드로 거절한다.
@Getter
@RequiredArgsConstructor
public enum UnavailableReason {
    COMPETITION_NOT_ACTIVE(ErrorCode.COMPETITION_NOT_ACTIVE),
    MARKET_NOT_SUPPORTED(ErrorCode.COMPETITION_MARKET_NOT_SUPPORTED),
    ORDER_CLOSED(ErrorCode.COMPETITION_ORDER_CLOSED),
    PRICE_UNAVAILABLE(ErrorCode.COMPETITION_PRICE_UNAVAILABLE);

    private final ErrorCode errorCode;
}
