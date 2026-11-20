package org.sejongisc.backend.competition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

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

    // 매수 대금·수수료를 뺀다. 잔고 검사를 여기서 해서 어디서 호출해도 음수가 되지 않는다.
    public void decreaseCash(BigDecimal amount) {
        if (cash.compareTo(amount) < 0) {
            throw new CustomException(ErrorCode.COMPETITION_INSUFFICIENT_CASH);
        }
        cash = cash.subtract(amount);
    }

    // 매도 대금에서 수수료를 뺀 금액을 넣는다.
    public void increaseCash(BigDecimal amount) {
        cash = cash.add(amount);
    }
}
