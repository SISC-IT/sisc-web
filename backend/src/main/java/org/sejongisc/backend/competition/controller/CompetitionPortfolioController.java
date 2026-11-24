package org.sejongisc.backend.competition.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.competition.dto.CompetitionHoldingResponse;
import org.sejongisc.backend.competition.dto.CompetitionSummaryResponse;
import org.sejongisc.backend.competition.dto.CompetitionTradeResponse;
import org.sejongisc.backend.competition.service.CompetitionPortfolioService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/competitions/{competitionId}/me")
@Tag(name = "수익률 대결 내 계좌 API", description = "수익률 대결 내 현황·거래 내역·보유 종목 조회 API")
public class CompetitionPortfolioController {

    private final CompetitionPortfolioService portfolioService;

    @Operation(
            summary = "내 거래 내역 조회",
            description = """
            로그인한 참가자의 거래 내역을 최근 체결순으로 반환합니다. \s
            - page는 0부터 시작합니다. size는 기본 20, 최대 100입니다. \s
            - 항목 형식은 매수·매도 주문 응답과 같습니다. cashBalance는 그 거래 직후 현금입니다. \s
            - 제외된 참가자와 끝난 대회도 조회할 수 있습니다.
            """
    )
    @GetMapping("/trades")
    public ResponseEntity<Page<CompetitionTradeResponse>> getTrades(
            @PathVariable UUID competitionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(portfolioService.getTrades(competitionId, userDetails.getUserId(), page, size));
    }

    @Operation(
            summary = "내 보유 종목 조회",
            description = """
            로그인한 참가자의 보유 종목을 평가액이 큰 순서로 반환합니다. \s
            - closePrice는 지금 공개된 최신 종가이고, evaluationAmount는 수량 × closePrice입니다. \s
            - profit은 평가액 − 수량 × 평균 매입가입니다. 평균 매입가에 수수료가 빠져 있어 손익에도 수수료는 들어가지 않습니다. \s
            - profitRate는 매입 금액 대비 비율입니다(0.05는 5%). \s
            - 종가가 아직 없는 종목은 closePrice부터 profitRate까지 null이고 목록 맨 뒤에 옵니다. \s
            - 제외된 참가자와 끝난 대회도 조회할 수 있습니다.
            """
    )
    @GetMapping("/holdings")
    public ResponseEntity<List<CompetitionHoldingResponse>> getHoldings(
            @PathVariable UUID competitionId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(portfolioService.getHoldings(competitionId, userDetails.getUserId()));
    }

    @Operation(
            summary = "내 날짜별 현황 조회",
            description = """
            로그인한 참가자의 그날 자산·수익률·참여 상태를 반환합니다. date를 생략하면 오늘(KST)이고, 오늘 이후 날짜는 400입니다. \s
            - 오늘은 PROVISIONAL입니다. 지금 잔액과 지금 공개된 종가로 평가합니다. 종가는 16:30에 공개되므로 그 전에는 전 거래일 종가입니다. valuedAt은 평가 시각입니다. \s
            - 지난 날짜는 그날 16:00 순위 스냅샷이 있으면 FINALIZED이고 totalAssets, profitLoss, returnRate를 스냅샷 값으로 줍니다. 스냅샷이 없는 날(주말·공휴일 등)은 PENDING_DATA입니다. 순위와 같은 기준이라 그날 저녁 거래의 수수료는 다음 거래일 값에 반영됩니다. \s
            - 지난 날짜의 cashBalance, depositBalance, stockValue는 날짜별로 남기지 않아 null입니다. \s
            - 오늘이라도 종가가 없는 보유 종목이 있으면 PENDING_DATA이고 평가 항목(stockValue, totalAssets, profitLoss, returnRate)은 null입니다. \s
            - totalAssets = cashBalance + depositBalance + stockValue, profitLoss = totalAssets − initialBalance입니다. \s
            - returnRate는 초기 자금 대비 비율입니다(0.05는 5%). 개인 순위의 profitRate와 계산식이 같고, 오늘 값은 순위보다 최신입니다. \s
            - totalFees, totalInterest는 그날 기준 누적입니다. totalInterest는 그날 0시까지 지급됐어야 할 이자(전날 밤까지)의 합입니다. \s
            - participationStatus는 그날까지 매수·매도를 한 번이라도 했으면 PARTICIPATED, 아니면 NOT_PARTICIPATED입니다. 예금 입출금은 참여로 치지 않습니다. EXCLUDED는 날짜와 상관없이 지금 제외 상태이면 반환합니다. \s
            - participatedOnDate, firstParticipatedAt, lastParticipatedAt은 그날의 매수·매도 기준입니다. \s
            - 대회가 라운드 1개이고 국내 주식만 거래해서 명세의 round, appliedExchangeRate는 없습니다.
            """
    )
    @GetMapping("/summary")
    public ResponseEntity<CompetitionSummaryResponse> getSummary(
            @PathVariable UUID competitionId,
            @RequestParam(required = false) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(portfolioService.getSummary(competitionId, userDetails.getUserId(), date));
    }
}
