package org.sejongisc.backend.competition.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.competition.dto.CompetitionHoldingResponse;
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
@Tag(name = "수익률 대결 내 계좌 API", description = "수익률 대결 내 거래 내역·보유 종목 조회 API")
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
}
