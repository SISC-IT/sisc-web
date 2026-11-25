package org.sejongisc.backend.competition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Builder
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AccountLedger extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID ledgerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private CompetitionAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LedgerType type;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cashAfter;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal depositAfter;

    // 이자가 어느 날짜 몫인지. 입출금은 null이다. (account_id, interest_date) 유니크
    private LocalDate interestDate;

    @Column(nullable = false)
    private LocalDateTime executedAt;
}
