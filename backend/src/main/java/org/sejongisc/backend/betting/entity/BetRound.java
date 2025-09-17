package org.sejongisc.backend.betting.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class BetRound {

    @Id @GeneratedValue
    @org.hibernate.annotations.UuidGenerator
    private UUID id;

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

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime openAt;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lockAt;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime settleAt;

    @CreatedDate
    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private BetOption resultOption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketType market;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal previousClosePrice;

    public enum Scope {DAILY, WEEKLY};
    public enum BetOption {RISE, FALL};
    public enum MarketType {KOREA, US}
}
