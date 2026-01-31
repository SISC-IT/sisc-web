package org.sejongisc.backend.backtest.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TradeType {
    BUY("매수"),
    SELL("매도"),
    SELL_FORCED("기간 만료 강제 청산");

    private final String description;
}