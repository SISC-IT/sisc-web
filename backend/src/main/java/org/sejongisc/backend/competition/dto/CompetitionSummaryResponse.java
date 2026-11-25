package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;

// 지난 날짜는 cashBalance, depositBalance, stockValue가 null이다(날짜별로 남기지 않음).
// PENDING_DATA는 평가 항목(stockValue, totalAssets, profitLoss, returnRate, valuedAt)도 null이다.
@Builder
public record CompetitionSummaryResponse(
        LocalDate date,
        ValuationStatus valuationStatus,
        LocalDateTime valuedAt,
        BigDecimal initialBalance,
        BigDecimal cashBalance,
        BigDecimal depositBalance,
        BigDecimal stockValue,
        BigDecimal totalAssets,
        BigDecimal profitLoss,
        BigDecimal returnRate,
        BigDecimal totalFees,
        BigDecimal totalInterest,
        ParticipationStatus participationStatus,
        boolean participatedOnDate,
        LocalDateTime firstParticipatedAt,
        LocalDateTime lastParticipatedAt
) {

    public enum ValuationStatus {
        PROVISIONAL,  // 당일 거래·데이터 반영 중
        FINALIZED,    // 날짜별 평가 완료
        PENDING_DATA  // 평가에 필요한 데이터 부족
    }

    public enum ParticipationStatus {
        PARTICIPATED,     // 매수·매도나 예금 입출금을 한 번이라도 했다
        NOT_PARTICIPATED, // 아직 아무 거래도 하지 않았다
        EXCLUDED          // 대회에서 제외됐다
    }
}
