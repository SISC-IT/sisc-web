package org.sejongisc.backend.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@AllArgsConstructor
public class PositionDto {
    String ticker;
    //수량
    Integer positionQty;
    //매입평균가
    BigDecimal avgPrice;
    //현재 주가
    BigDecimal currentPrice;
    //평가금액 : 수량 * 현재 주가
    BigDecimal marketPrice;
    //이익,손해금액
    BigDecimal pnl;
    //이익,손해율
    BigDecimal pnlRate;

    public PositionDto setPnl() {

        BigDecimal qty = BigDecimal.valueOf(positionQty);

        // 평가금액 계산 (필요할 경우)
        if (marketPrice == null) {
            marketPrice = currentPrice.multiply(qty);
        }

        // 투자원금 = avgPrice × qty
        BigDecimal invested = avgPrice.multiply(qty);

        // 손익금액 = marketPrice - 투자원금
        this.pnl = marketPrice.subtract(invested);

        // 손익률 = pnl / 투자원금
        if (invested.compareTo(BigDecimal.ZERO) != 0) {
            this.pnlRate = pnl.divide(invested, 6, RoundingMode.HALF_UP);
        } else {
            this.pnlRate = BigDecimal.ZERO;
        }

        return this;
    }

}
