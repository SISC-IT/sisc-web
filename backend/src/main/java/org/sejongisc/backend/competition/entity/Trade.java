package org.sejongisc.backend.competition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Builder
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Trade extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID tradeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private CompetitionAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private CompetitionStock stock;

    // 체결에 쓴 종가. 사용자가 확인한 종가(closePriceId)와 같은지 비교한 그 행이다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "close_price_id", nullable = false)
    private DailyClosePrice closePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private TradeSide side;

    @Column(nullable = false)
    private Long quantity;

    // 체결가. 거래 행만 보고 무엇을 얼마에 거래했는지 알 수 있도록 종가를 복사해 둔다.
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    // 실제로 현금에서 빠진 수수료. 나중에 수수료율·반올림 규칙이 바뀌어도 기록이 어긋나지 않게 금액으로 남긴다.
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal fee;

    // 클라이언트가 보낸 중복 주문 방지 키. (account_id, idempotency_key) 유니크
    @Column(nullable = false, columnDefinition = "uuid")
    private UUID idempotencyKey;

    @Column(nullable = false)
    private LocalDateTime executedAt;
}
