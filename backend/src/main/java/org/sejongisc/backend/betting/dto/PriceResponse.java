package org.sejongisc.backend.betting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.sejongisc.backend.betting.entity.MarketType;
import org.sejongisc.backend.stock.entity.PriceData;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "시세 응답 DTO (PriceData 기반)")
public class PriceResponse {

    @Schema(description = "티커 이름 (예: KOSPI, BTC, AAPL)")
    private String name;

    @Schema(description = "심볼 (거래 대상의 식별자)")
    private String symbol;

    @Schema(description = "시장 구분 (예: KOREA, US, CRYPTO)")
    private MarketType market;

    @Schema(description = "이전 종가 (기준가)")
    private BigDecimal previousClosePrice;

    @Schema(description = "정산 종가 (결과 비교용)")
    private BigDecimal settleClosePrice;


    public static PriceResponse from(PriceData entity, MarketType marketName) {
        return PriceResponse.builder()
                .name(entity.getTicker())
                .symbol(entity.getTicker())
                .market(marketName)
                .previousClosePrice(entity.getClosePrice())
                .settleClosePrice(entity.getAdjustedClose())
                .build();
    }
}
