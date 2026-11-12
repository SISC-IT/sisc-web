package org.sejongisc.backend.competition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Builder
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Competition extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID competitionId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CompetitionStatus status;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal initialCash;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal feeRate;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal depositInterestRate;
}
