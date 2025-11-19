package org.sejongisc.backend.betting.dto;

import lombok.Builder;
import lombok.Getter;
import org.sejongisc.backend.betting.entity.BetOption;
import org.sejongisc.backend.betting.entity.BetRound;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class BetRoundResponse {
    private UUID betRoundId;
    private String title;
    private String symbol;
    private BigDecimal previousClosePrice;
    private LocalDateTime openAt;
    private LocalDateTime lockAt;

    // 통계 정보
    private int upBetCount;
    private int downBetCount;
    private long upTotalPoints;
    private long downTotalPoints;

    // 예상 획득 포인트 (100포인트 베팅 기준 예시)
    private BigDecimal expectedUpReward;
    private BigDecimal expectedDownReward;

    public static BetRoundResponse from(BetRound round) {
        return BetRoundResponse.builder()
                .betRoundId(round.getBetRoundID())
                .title(round.getTitle())
                .symbol(round.getSymbol())
                .previousClosePrice(round.getPreviousClosePrice())
                .openAt(round.getOpenAt())
                .lockAt(round.getLockAt())
                .upBetCount(round.getUpBetCount())
                .downBetCount(round.getDownBetCount())
                .upTotalPoints(round.getUpTotalPoints())
                .downTotalPoints(round.getDownTotalPoints())
                // 예상 배당률 계산 (소수점 등 로직은 기획에 맞춰 조정)
                .expectedUpReward(round.getEstimatedRewardMultiplier(BetOption.RISE))
                .expectedDownReward(round.getEstimatedRewardMultiplier(BetOption.FALL))
                .build();
    }
}