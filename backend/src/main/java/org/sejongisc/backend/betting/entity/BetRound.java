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
    private boolean status = false;

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
        if (!this.status) throw new IllegalStateException("이미 종료된 라운드입니다.");
        this.status = false;
        this.settleAt = LocalDateTime.now();
    }

    public void validateBettable() {
        if (isClosed()) {
            throw new CustomException(ErrorCode.BET_TIME_INVALID);
        }
    }
}
