package org.sejongisc.backend.competition.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.competition.dto.CompetitionStockDetailResponse;
import org.sejongisc.backend.competition.dto.CompetitionStockListResponse;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.service.CompetitionStockService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/competitions/{competitionId}/stocks")
@Tag(name = "수익률 대결 종목 API", description = "수익률 대결 종목 조회 API")
public class CompetitionStockController {

    private final CompetitionStockService stockService;

    @Operation(
            summary = "종목 목록 검색",
            description = """
            회사명 또는 종목코드로 검색하고 종가와 전 거래일 대비 등락률을 반환합니다. \s
            - market을 생략하면 전체 시장을 조회합니다. \s
            - page는 1부터 시작하고 size는 최대 100입니다. \s
            - 거래 가능한 종가가 없으면 가격 항목은 null이고 tradable은 false입니다. \s
            - tradable은 종가 유무만 반영합니다.
            """
    )
    @GetMapping
    public ResponseEntity<CompetitionStockListResponse> search(
            @PathVariable UUID competitionId,
            @RequestParam(required = false) Market market,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(stockService.search(competitionId, market, query, page, size));
    }

    @Operation(
            summary = "종목 상세 조회",
            description = """
            종목의 종가와 로그인한 참가자의 보유 수량, 현금 잔고를 반환합니다. \s
            - 보유하지 않은 종목은 holdingQuantity가 0이고 평가 항목은 null입니다. \s
            - 대회 참가자가 아니면 404를 반환합니다.
            """
    )
    @GetMapping("/{ticker}")
    public ResponseEntity<CompetitionStockDetailResponse> getDetail(
            @PathVariable UUID competitionId,
            @PathVariable String ticker,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(stockService.getDetail(competitionId, ticker, userDetails.getUserId()));
    }
}
