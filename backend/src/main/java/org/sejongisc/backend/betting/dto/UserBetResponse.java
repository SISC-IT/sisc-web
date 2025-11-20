package org.sejongisc.backend.betting.dto;

import lombok.Builder;
import lombok.Getter;
import org.sejongisc.backend.betting.entity.BetOption;
import org.sejongisc.backend.betting.entity.BetStatus;
import org.sejongisc.backend.betting.entity.UserBet;

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

    // Entity -> DTO 변환 메서드
    public static UserBetResponse from(UserBet bet) {
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
                .isCorrect(bet.isCollect())        // boolean 타입의 Getter는 isCorrect()
                .earnedPoints(bet.getPayoutPoints()) // 엔티티 필드명이 payoutPoints임
                .build();
    }
}