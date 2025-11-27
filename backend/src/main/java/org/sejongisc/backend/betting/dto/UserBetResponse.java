package org.sejongisc.backend.betting.dto;

import lombok.Builder;
import lombok.Getter;
import org.sejongisc.backend.betting.entity.BetOption;
import org.sejongisc.backend.betting.entity.BetRound;
import org.sejongisc.backend.betting.entity.BetStatus;
import org.sejongisc.backend.betting.entity.UserBet;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class UserBetResponse {
    private UUID userBetId;
    private UUID betRoundId;
    private String roundTitle;  // BetRound의 제목
    private String symbol;      // BetRound의 종목명
    private BetOption option;
    private boolean isFree;
    private Integer stakePoints;
    private BetStatus betStatus;
    private Boolean isCorrect;  // 결과 (성공 여부)
    private Integer earnedPoints;

    // [추가] 인원수 정보를 상세하게 분리
    private BigDecimal previousClosePrice; // 종가 (베팅 기준)
    private BigDecimal settleClosePrice;   // 다음 날 종가 (정산 결과)

    private int upBetCount;    // 상승 베팅 인원
    private int downBetCount;  // 하락 베팅 인원

    // Entity -> DTO 변환 메서드
    public static UserBetResponse from(UserBet bet) {
        BetRound round = bet.getRound();

        return UserBetResponse.builder()
                .userBetId(bet.getUserBetId())
                // 여기서 bet.getRound()를 호출할 때 영속성 컨텍스트가 살아있어야 함 (Service 내부)
                .betRoundId(bet.getRound().getBetRoundID())
                .roundTitle(bet.getRound().getTitle())
                .symbol(bet.getRound().getSymbol())
                .option(bet.getOption())
                .isFree(bet.isFree())
                .stakePoints(bet.getStakePoints())
                .betStatus(bet.getBetStatus())
                .isCorrect(bet.isCorrect())        // boolean 타입의 Getter는 isCorrect()
                .earnedPoints(bet.getPayoutPoints()) // 엔티티 필드명이 payoutPoints임
                .previousClosePrice(round.getPreviousClosePrice())
                .settleClosePrice(round.getSettleClosePrice())
                .upBetCount(round.getUpBetCount())     // 상승 인원
                .downBetCount(round.getDownBetCount()) // 하락 인원
                .build();
    }
}