package org.sejongisc.backend.backtest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 백테스팅 거래 기록 (불변 객체)
 */
public record TradeLog(
        TradeType type,       // Enum 변경 적용
        LocalDateTime time,
        BigDecimal price,
        BigDecimal shares
) {}