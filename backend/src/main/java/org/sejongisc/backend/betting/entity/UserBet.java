package org.sejongisc.backend.betting.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class UserBet {

    @Id @GeneratedValue
    @org.hibernate.annotations.UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private BetRound round;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BetRound.BetOption option;

    private boolean isFree;

    @Column(nullable = false)
    private Integer stakePoints;

    @Column(nullable = true)
    private Integer payoutPoints;

    @CreatedDate
    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt; // 일단 미사용

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public enum Status{OPEN, CLOSED};
}
