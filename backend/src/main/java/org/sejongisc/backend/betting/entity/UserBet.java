package org.sejongisc.backend.betting.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.util.UUID;

@Entity
@Table(
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_bet_round_user",
        columnNames = {"round_id", "user_id"}
    ),
    indexes = {
        @Index(name = "idx_user_bet_user", columnList = "user_id"),
        @Index(name = "idx_user_bet_round", columnList = "round_id")
    }
)
@Getter
@Builder @NoArgsConstructor @AllArgsConstructor
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

    @Column(nullable = false)
    private boolean isFree;

    @Column(nullable = false)
    private Integer stakePoints;

    @Column(nullable = true)
    private Integer payoutPoints;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BetStatus betStatus;

    private boolean isCollect;

    public void win(int reward) {
        this.payoutPoints = reward;
        this.isCollect = true;
        this.betStatus = BetStatus.CLOSED;
    }

    public void lose() {
        this.payoutPoints = 0;
        this.isCollect = false;
        this.betStatus = BetStatus.CLOSED;
    }

}
