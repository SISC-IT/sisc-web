package org.sejongisc.backend.backtest.dto;
/**
 * 전략 조건 한 줄 (Operand + Operator + Operand)
 * 예: [SMA(20)] [GT] [Close]
 */

import io.swagger.v3.oas.annotations.media.Schema;

public record StrategyCondition(

        @Schema(description = "좌향 (Operand)")
        StrategyOperand leftOperand,

        @Schema(description = "연산자 (예: \"GT\", \"LT\", \"CROSSES_ABOVE\")")
        String operator,

        @Schema(description = "우향 (Operand)")
        StrategyOperand rightOperand,

        @Schema(description = "\"무조건 행동\" 조건인지 여부 (true = OR 조건, false = AND 조건)")
        boolean isAbsolute
) {}