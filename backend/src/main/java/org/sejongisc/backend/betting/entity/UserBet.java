package org.sejongisc.backend.betting.entity;

import jakarta.persistence.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.util.UUID;

@Entity
public class UserBet extends BasePostgresEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID userBetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private BetRound round;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BetOption option;

    private Boolean isFree;

    @Column(nullable = false)
    private Integer stakePoints;

    @Column(nullable = true)
    private Integer payoutPoints;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BetStatus betStatus;
}
