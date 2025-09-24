package org.sejongisc.backend.betting.entity;

import jakarta.persistence.*;
import org.sejongisc.backend.betting.enums.BetOption;
import org.sejongisc.backend.betting.enums.MarketType;
import org.sejongisc.backend.betting.enums.Scope;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class BetRound extends BasePostgresEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
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
    
    //private LocalDateTime createdAt; // 상속 받음 (createdDate)

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private BetOption resultOption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketType market;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal previousClosePrice;
}
