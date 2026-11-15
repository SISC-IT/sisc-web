package org.sejongisc.backend.competition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Builder
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CompetitionAccount extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID accountId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false, unique = true)
    private CompetitionParticipant participant;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cash;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal deposit;

    // 낙관적 락: 동시에 들어온 요청 중 늦게 저장하는 쪽을 실패시켜 갱신 손실을 막는다.
    @Version
    private Long version;
}
