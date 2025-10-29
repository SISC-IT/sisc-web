package org.sejongisc.backend.betting.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketType market;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal previousClosePrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal settleClosePrice;
}
