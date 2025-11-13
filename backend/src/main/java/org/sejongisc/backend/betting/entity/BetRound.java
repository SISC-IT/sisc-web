package org.sejongisc.backend.betting.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BetRound extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    @Schema(description = "베팅 라운드의 고유 식별자")
    private UUID betRoundID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "라운드 단위")
    private Scope scope;

    @Column(nullable = false, length = 100)
    @Schema(description = "라운드 제목")
    private String title;

    @Column(nullable = false, length = 50)
    @Schema(description = "베팅 대상 심볼")
    private String symbol;

    @Column(nullable = false)
    @Schema(description = "무료 베팅 허용 여부")
    private boolean allowFree;

    @Column(precision = 6, scale = 3)
    @Schema(description = "기본 배당 배율")
    private BigDecimal baseMultiplier;

    @Column(nullable = false)
    @Schema(description = "라운드 진행 상태", defaultValue = "false")
    private boolean status = false; // Todo : Enum 클래스로 변경 고려

    @Schema(description = "베팅이 열리는 시각 (유저 참여 시작 시점)")
    private LocalDateTime openAt;

    @Schema(description = "베팅이 잠기는 시각")
    private LocalDateTime lockAt;

    @Schema(description = "결과 정산 시각")
    private LocalDateTime settleAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    @Schema(description = "최종 결과")
    private BetOption resultOption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "시장 구분")
    private MarketType market;

    @Column(precision = 15, scale = 2, nullable = false)
    @Schema(description = "이전 종가 (베팅 기준 가격)")
    private BigDecimal previousClosePrice;

    @Column(precision = 15, scale = 2)
    @Schema(description = "정산 종가 (결과 비교용)")
    private BigDecimal settleClosePrice;

    // 라운드가 현재 진행 중인지 여부 반환
    public boolean isOpen() {
        return this.status;
    }

    // 라운드가 종료되었는지 여부 반환
    public boolean isClosed() {
        return !this.status;
    }

    // 베팅 시작
    public void open() {
        this.status = true;
    }

    // 베팅 불가
    public void close() {
        this.status = false;
    }

    // "베팅 가능한 상태인지 검증
    public void validate() {
        if (isClosed() || (lockAt != null && LocalDateTime.now().isAfter(lockAt))) {
            throw new CustomException(ErrorCode.BET_ROUND_CLOSED);
        }
    }

    // 정산 로직 수행
    public void settle(BigDecimal finalPrice) {
        if (isOpen()) {
            throw new CustomException(ErrorCode.BET_ROUND_NOT_CLOSED);
        }
        if (this.settleAt != null) {
            return;
        }
        if (finalPrice == null) {
            throw new IllegalArgumentException("finalPrice must not be null");
        }
        this.settleClosePrice = finalPrice;
        this.resultOption = determineResult(finalPrice);
        this.settleAt = LocalDateTime.now();
    }

    // 결과 판정 로직 - 이전 종가와 비교하여 상승/하락 결정
    private BetOption determineResult(BigDecimal finalPrice) {
        int compare = finalPrice.compareTo(previousClosePrice);
        if (compare >= 0) return BetOption.RISE;
        return BetOption.FALL;
    }
}
