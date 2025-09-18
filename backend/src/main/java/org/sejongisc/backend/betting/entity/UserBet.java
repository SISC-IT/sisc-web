package org.sejongisc.backend.betting.entity;

import jakarta.persistence.*;
import org.sejongisc.backend.betting.enums.Status;
import org.sejongisc.backend.betting.enums.BetOption;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class UserBet extends BasePostgresEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private BetRound round;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BetOption option;

    private boolean isFree;

    @Column(nullable = false)
    private Integer stakePoints;

    @Column(nullable = true)
    private Integer payoutPoints;

    // private LocalDateTime createdAt; // 상속 받음 (createdDate)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
}
