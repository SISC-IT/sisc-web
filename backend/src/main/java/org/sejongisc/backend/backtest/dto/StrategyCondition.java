package org.sejongisc.backend.backtest.dto;

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

    // 좌항
    private StrategyOperand leftOperand;

    // 연산자 (예: "GT", "LT", "CROSSES_ABOVE")
    private String operator;

    // 우항
    private StrategyOperand rightOperand;

    /**
     * "무조건 행동" 조건인지 여부
     * true = 이 조건이 맞으면 다른 '일반' 조건 무시
     * false = '일반' 조건
     */
    private boolean isAbsolute;
}