package org.sejongisc.backend.backtest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전략 조건 한 줄 (Operand + Operator + Operand)
 * 예: [SMA(20)] [GT] [Close]
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyCondition {

    @Schema(description = "좌향")
    private StrategyOperand leftOperand;

    @Schema(description = "연산자 (예: \"GT\", \"LT\", \"CROSSES_ABOVE\")")
    private String operator;

    @Schema(description = "우향")
    private StrategyOperand rightOperand;

    @Schema(description = "\"무조건 행동\" 조건인지 여부 (true = 이 조건이 맞으면 다른 '일반' 조건 무시, false = 이 조건은 일반 조건)")
    private boolean isAbsolute;
}