package org.sejongisc.backend.backtest.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 전략 조건의 개별 항 (Operand)
 * 예: SMA(20), 종가(Close), 30(상수)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyOperand {

    // 항의 타입: "indicator", "price", "const"
    private String type;

    // type == "indicator" 일 때
    // 지표 코드 (예: "SMA", "RSI", "MACD")
    private String indicatorCode;

    // type == "price" 일 때
    // 가격 필드 (예: "Close", "Open", "High", "Low", "Volume")
    private String priceField;

    // type == "const" 일 때
    // 상수 값 (예: 30, 0.02)
    private BigDecimal constantValue;

    // 지표의 출력값 (예: "value", "macd", "signal", "hist")
    private String output;

    // 지표의 파라미터 맵 (예: {"length": 20})
    private Map<String, Object> params;
}