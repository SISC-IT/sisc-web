package org.sejongisc.backend.betting.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Builder
@Getter
@NoArgsConstructor @AllArgsConstructor
public class BetRound extends BasePostgresEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID betRoundID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Scope scope;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 50)
    private String symbol;

    private boolean allowFree;

    @Column(precision = 6, scale = 3)
    private BigDecimal baseMultiplier;

    private boolean status; // enum 고려할 것

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
}
