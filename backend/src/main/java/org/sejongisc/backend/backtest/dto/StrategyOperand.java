package org.sejongisc.backend.backtest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record StrategyOperand(

        @Schema(description = "유형 (price: 가격데이터, const: 상수, indicator: 보조지표)", example = "indicator")
        @NotNull(message = "피연산자 유형은 필수입니다.")
        String type, // 'price', 'const', 'indicator'

        @Schema(description = "지표 코드 (SMA, EMA, RSI, MACD, BB, STOCH, CCI, ATR, ADX)", example = "BB")
        String indicatorCode,

        @Schema(description = "가격 기준 (Close, Open, High, Low, Volume)", example = "Close")
        String priceField,

        @Schema(description = "상수 값 (type이 const일 때 사용)", example = "30")
        Double constantValue,

        @Schema(description = """
        지표별 결과값 선택 (다중 출력 지표용):
        - BB (볼린저밴드): upper, middle, lower
        - MACD: macd, signal, hist
        - STOCH (스토캐스틱): k, d
        - 나머지 단일 지표는 null 혹은 생략 가능
        """, example = "lower")
        String output,

        @Schema(description = """
        지표별 필수 파라미터 (Map 형식):
        - SMA, EMA, RSI, CCI, ATR, ADX: { "length": 14 }
        - MACD: { "fast": 12, "slow": 26, "signal": 9 }
        - BB (볼린저밴드): { "length": 20, "k": 2.0 }
        - STOCH (스토캐스틱): { "kLength": 14, "dLength": 3 }
        """, example = "{\"length\": 20, \"k\": 2.0}")
        @NotNull(message = "파라미터 맵은 필수입니다. (비어있더라도 {} 전달)")
        Map<String, Object> params
) {}