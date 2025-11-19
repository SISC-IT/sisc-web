package org.sejongisc.backend.backtest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 전략 조건의 개별 항 (Operand)
 * 예: SMA(20), 종가(Close), 30(상수)
 */
@Getter
@NoArgsConstructor
@Setter
public class StrategyOperand {

    @Schema(description = "항의 타입: \"indicator\", \"price\", \"const\"")
    private String type;

    @Schema(description = "type == \"indicator\" 일 때의 지표 코드 (예: \"SMA\", \"RSI\", \"MACD\")")
    private String indicatorCode;

    @Schema(description = "type == \"price\" 일 때의 가격 필드 (예: \"Close\", \"Open\", \"High\", \"Low\", \"Volume\")")
    private String priceField;

    @Schema(description = "type == \"const\" 일 때의 상수 값 (예: 30, 0.02)")
    private BigDecimal constantValue;

    @Schema(description = "지표의 출력값 (예: \"value\", \"macd\", \"signal\", \"hist\")")
    private String output;

    @Schema(description = "지표의 파라미터 맵 (예: {\"length\": 20})")
    private Map<String, Object> params;

    //private String transform;     // 거래량 관련 필드, 추후 적용 고려
}