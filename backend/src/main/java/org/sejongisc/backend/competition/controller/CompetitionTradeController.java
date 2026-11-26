package org.sejongisc.backend.competition.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.competition.dto.CompetitionTradeRequest;
import org.sejongisc.backend.competition.dto.CompetitionTradeResponse;
import org.sejongisc.backend.competition.service.CompetitionTradeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/competitions/{competitionId}/me/trades")
@Tag(name = "수익률 대결 거래 API", description = "수익률 대결 매수·매도 API")
public class CompetitionTradeController {

    private final CompetitionTradeService tradeService;

    @Operation(
            summary = "매수·매도 주문",
            description = """
            확인한 종가(closePriceId)로 국내 종목을 매수하거나 매도합니다. \s
            - 주문 가능 시간은 16:30~21:00이고, 오늘 종가가 있어야 주문할 수 있습니다(주말·공휴일, 종가 수집이 늦거나 실패한 종목은 불가). \s
            - 종료일 종가로 최종 평가하므로 주문은 종료 전날까지 받습니다. \s
            - 확인한 종가가 최신 종가와 다르면 409 PRICE_CHANGED를 반환합니다. \s
            - Idempotency-Key 헤더에 주문마다 새 UUID를 보냅니다. 같은 키로 다시 보내면 새로 체결하지 않고 처음 결과를 반환합니다. \s
            - 409 COMPETITION_ORDER_CONFLICT는 같은 계좌의 다른 요청과 겹친 경우입니다. 같은 Idempotency-Key로 다시 보내면 처음 주문이 체결됐을 때 그 결과를 받습니다. \s
            - 수수료는 체결 금액 × 대회 수수료율이고, 원 미만을 버립니다.
            """
    )
    @PostMapping
    public ResponseEntity<CompetitionTradeResponse> order(
            @PathVariable UUID competitionId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CompetitionTradeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(tradeService.order(competitionId, userDetails.getUserId(), idempotencyKey, request));
    }
}
