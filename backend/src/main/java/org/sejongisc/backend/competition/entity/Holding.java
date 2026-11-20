package org.sejongisc.backend.competition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Builder
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Holding extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID holdingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private CompetitionAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private CompetitionStock stock;

    @Column(nullable = false)
    private Long quantity; // 0주가 되면 행을 삭제한다

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal avgBuyPrice; // 수수료 제외 체결가 평균

    // 추가 매수: (기존 평단 × 기존 수량 + 체결가 × 새 수량) ÷ 총 수량, DB 소수 자릿수(4)에서 반올림
    public void buy(long buyQuantity, BigDecimal price) {
        long total = quantity + buyQuantity;
        avgBuyPrice = avgBuyPrice.multiply(BigDecimal.valueOf(quantity))
                .add(price.multiply(BigDecimal.valueOf(buyQuantity)))
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        quantity = total;
    }

    // 매도: 수량만 줄이고 평단은 그대로 둔다. 0주가 되면 호출한 쪽이 행을 삭제한다.
    public void sell(long sellQuantity) {
        if (sellQuantity > quantity) {
            throw new CustomException(ErrorCode.COMPETITION_INSUFFICIENT_HOLDING);
        }
        quantity -= sellQuantity;
    }
}
