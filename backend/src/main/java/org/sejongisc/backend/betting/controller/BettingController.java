package org.sejongisc.backend.betting.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.betting.dto.BetRoundResponse;
import org.sejongisc.backend.betting.dto.UserBetRequest;
import org.sejongisc.backend.betting.entity.BetRound;
import org.sejongisc.backend.betting.entity.Scope;
import org.sejongisc.backend.betting.entity.UserBet;
import org.sejongisc.backend.betting.service.BettingService;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api")
@Tag(name = "Betting API", description = "베팅 관련 기능을 제공합니다.")
public class BettingController {

    private final BettingService bettingService;

    @Operation(
            summary = "오늘의 베팅 라운드 조회",
            description = """
            요청된 범위(`daily` 또는 `weekly`)에 해당하는 현재 활성화된 베팅 라운드를 조회합니다.
            라운드가 없을 경우 404(Not Found)를 반환합니다.

            내부 로직:
            - `Scope` 값(daily/weekly)에 맞는 라운드 중 `status = true`인 것을 반환
            - 없으면 `Optional.empty()` 처리
            """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "활성 라운드 조회 성공"),
                    @ApiResponse(responseCode = "404", description = "활성 라운드가 존재하지 않음")
            }
    )
    @GetMapping("/bet-rounds/{scope}")
    public ResponseEntity<BetRoundResponse> getTodayBetRound(
            @Parameter(description = "라운드 범위 (Scope): DAILY 또는 WEEKLY", example = "DAILY")
            @PathVariable Scope scope
    ) {
        Optional<BetRoundResponse> betRound = bettingService.getActiveRoundResponse(scope);

        return betRound
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    @Operation(
            summary = "전체 베팅 라운드 이력 조회",
            description = """
            지금까지 생성된 모든 베팅 라운드 이력을 최신 정산일(`settleAt`) 기준으로 내림차순 정렬하여 반환합니다.
            (필요 시 추후 정렬·검색 기능이 추가될 수 있습니다.)
            """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "모든 베팅 라운드 조회 성공")
            }
    )
    @GetMapping("/bet-rounds/history")
    public ResponseEntity<List<BetRound>> getAllBetRounds() {
        List<BetRound> betRounds = bettingService.getAllBetRounds();
        return ResponseEntity.ok(betRounds);
    }

    @Operation(
            summary = "유저 베팅 등록",
            description = """
            현재 로그인된 사용자가 선택한 옵션(상승/하락 등)에 대해 베팅을 등록합니다.
            무료 베팅(`isFree = true`)인 경우 포인트 차감이 없으며,
            유료 베팅(`isFree = false`)일 경우 포인트가 차감되어 `PointHistory`에 기록됩니다.
            """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "베팅 등록 성공"),
                    @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
                    @ApiResponse(responseCode = "404", description = "존재하지 않는 라운드"),
                    @ApiResponse(responseCode = "409", description = "중복 베팅, 베팅 시간 아님, 또는 포인트 부족")
            }
    )
    @PostMapping("/user-bets")
    public ResponseEntity<UserBet> postUserBet(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody UserBetRequest userBetRequest) {

        UserBet userBet = bettingService.postUserBet(principal.getUserId(), userBetRequest);
        return ResponseEntity.ok(userBet);
    }

    @Operation(
            summary = "유저 베팅 취소",
            description = """
            자신이 등록한 베팅을 취소합니다.
            단, 해당 라운드의 `lockAt` 시간 이전까지만 취소 가능하며,
            포인트가 사용된 베팅의 경우 취소 시 포인트가 복원됩니다.
            """,
            responses = {
                    @ApiResponse(responseCode = "204", description = "베팅 취소 성공"),
                    @ApiResponse(responseCode = "404", description = "해당 베팅이 존재하지 않음"),
                    @ApiResponse(responseCode = "409", description = "라운드가 이미 마감되어 취소 불가")
            }
    )
    @DeleteMapping("/user-bets/{userBetId}")
    public ResponseEntity<Void> cancelUserBet(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "취소할 베팅 ID", example = "3f57bcdc-7c4a-49a1-a1cb-0c2f8a5ef9ab")
            @PathVariable UUID userBetId) {

        bettingService.cancelUserBet(principal.getUserId(), userBetId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "내 베팅 이력 조회",
            description = """
            로그인된 사용자의 모든 베팅 이력을 최신 라운드 순으로 조회합니다.
            추후 특정 기간, 상태(진행중/정산완료) 등으로 필터링 기능이 추가될 수 있습니다.
            """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    @GetMapping("/user-bets/history")
    public ResponseEntity<List<UserBet>> getAllUserBets(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal) {

        List<UserBet> userBets = bettingService.getAllMyBets(principal.getUserId());
        return ResponseEntity.ok(userBets);
    }
}
