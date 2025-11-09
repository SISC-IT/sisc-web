package org.sejongisc.backend.betting.entity;

import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BetRound extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID betRoundID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Scope scope;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Column(nullable = false)
    private boolean allowFree;

    @Column(precision = 6, scale = 3)
    private BigDecimal baseMultiplier;

    @Column(nullable = false)
    private boolean status = false; // Todo : Enum 클래스로 수정

    private LocalDateTime openAt;

    private LocalDateTime lockAt;

    private LocalDateTime settleAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private BetOption resultOption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketType market;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal previousClosePrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal settleClosePrice;

    public boolean isOpen() {
        return this.status;
    }

    public boolean isClosed() {
        return !this.status;
    }

    public void open() {
        this.status = true;
    }

    public void close() {
        this.status = false;
    }

    public void validate() {
        if (isClosed() || (lockAt != null && LocalDateTime.now().isAfter(lockAt))) {
            throw new CustomException(ErrorCode.BET_ROUND_CLOSED);
        }
    }

    public void settle(BigDecimal finalPrice) {
        if (isOpen()) {
            throw new CustomException(ErrorCode.BET_ROUND_NOT_CLOSED);
        }
        if (this.settleAt != null) {
            return;
        }
        if (finalPrice == null) {
            throw new IllegalArgumentException("finalPrice must not be null");
        }
        this.settleClosePrice = finalPrice;
        this.resultOption = determineResult(finalPrice);
        this.settleAt = LocalDateTime.now();
    }

    private BetOption determineResult(BigDecimal finalPrice) {
        int compare = finalPrice.compareTo(previousClosePrice);

        if (compare >= 0) return BetOption.RISE;
        return BetOption.FALL;
    }

}
