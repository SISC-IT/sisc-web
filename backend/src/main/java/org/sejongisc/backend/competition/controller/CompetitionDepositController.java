package org.sejongisc.backend.competition.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.competition.dto.DepositTransactionRequest;
import org.sejongisc.backend.competition.dto.DepositTransactionResponse;
import org.sejongisc.backend.competition.service.CompetitionDepositService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/competitions/{competitionId}/me/deposit-transactions")
@Tag(name = "수익률 대결 예금 API", description = "수익률 대결 예금 입금·출금, 예금 내역 조회 API")
public class CompetitionDepositController {

    private final CompetitionDepositService depositService;

    @Operation(
            summary = "예금 입금·출금",
            description = """
            현금을 예금으로 옮기거나(DEPOSIT) 예금을 현금으로 옮깁니다(WITHDRAW). \s
            - 금액은 1원 이상의 정수입니다. \s
            - 입출금 가능 시간은 주문과 같은 16:30~21:00입니다. 주말에도 이 시간에는 입출금할 수 있습니다. 주문처럼 종료 전날까지 받습니다. \s
            - 예금에는 매일 00:00에 전날 밤 예금 × 연 이율 ÷ 365의 이자가 붙습니다(소수 4자리에서 버림). \s
            - 같은 요청을 두 번 보내면 두 번 옮겨집니다. 반대 방향으로 옮기면 되돌릴 수 있습니다. \s
            - 409 COMPETITION_TRANSFER_CONFLICT는 같은 계좌의 주문·입출금과 겹친 경우입니다. 다시 시도하면 됩니다.
            """
    )
    @PostMapping
    public ResponseEntity<DepositTransactionResponse> transfer(
            @PathVariable UUID competitionId,
            @Valid @RequestBody DepositTransactionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(depositService.transfer(competitionId, userDetails.getUserId(), request));
    }

    @Operation(
            summary = "예금 내역 조회",
            description = """
            로그인한 참가자의 입금·출금·이자 내역을 최근순으로 반환합니다. \s
            - page는 0부터 시작합니다. size는 기본 20, 최대 100입니다. \s
            - type은 DEPOSIT(입금), WITHDRAW(출금), INTEREST(이자)입니다. interestDate는 이자가 어느 날짜 몫인지이고, 입금·출금은 null입니다. \s
            - cashBalance, depositBalance는 그 기록 직후 잔액입니다. \s
            - 제외된 참가자와 끝난 대회도 조회할 수 있습니다.
            """
    )
    @GetMapping
    public ResponseEntity<Page<DepositTransactionResponse>> getTransactions(
            @PathVariable UUID competitionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(depositService.getTransactions(competitionId, userDetails.getUserId(), page, size));
    }
}
