package org.sejongisc.backend.backtest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 백테스트 실행 요청 시 Body에 담길 메인 DTO
 * (이 객체가 BacktestRun.paramsJson에 직렬화되어 저장됨)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BacktestRunRequest {

    @Schema(description = "초기 자본금", defaultValue = "10000000")
    private BigDecimal initialCapital;

    @Schema(description = "대상 종목 티커", defaultValue = "AAPL")
    private String ticker;

    @Schema(description = "매수 조건 그룹")
    private List<StrategyCondition> buyConditions;

    @Schema(description = "매도 조건 그룹")
    private List<StrategyCondition> sellConditions;

    @Schema(description = "노트", defaultValue = "골든크로스 + RSI 필터 전략 테스트")
    private String note;

    //@Schema(description = "거래 시 매수 비중", defaultValue = "10")
    //private int buyRatio;
    //@Schema(description = "거래 시 매도 비중", defaultValue = "10")
    //private int sellRatio;
    /*
     * 타임 프레임 (예: "D", "W", "M")
     private String timeFrame;
    */
}