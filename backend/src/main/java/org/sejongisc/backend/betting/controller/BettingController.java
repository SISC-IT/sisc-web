package org.sejongisc.backend.betting.controller;


import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
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
public class BettingController {

    private final BettingService bettingService;

    @GetMapping("/bet-rounds/{scope}")
    public ResponseEntity<BetRound> getTodayBetRound(
            @PathVariable @Pattern(regexp = "daily|weekly") String scope){

        Scope scopeEnum = Scope.valueOf(scope.toUpperCase());

        Optional<BetRound> betRound = bettingService.getActiveRound(scopeEnum);

        return betRound
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/bet-rounds/history")
    public ResponseEntity<List<BetRound>> getAllBetRounds(){
        List<BetRound> betRounds = bettingService.getAllBetRounds();

        return ResponseEntity.ok(betRounds);
    }

    @PostMapping("/user-bets")
    public ResponseEntity<UserBet> postUserBet(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody @Valid UserBetRequest userBetRequest){

        UserBet userBet = bettingService.postUserBet(principal.getUserId(), userBetRequest);

        return ResponseEntity.ok(userBet);
    }

    @DeleteMapping("/user-bets/{userBetId}")
    public ResponseEntity<Void> cancelUserBet(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID userBetId){

        bettingService.cancelUserBet(principal.getUserId(), userBetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user-bets/history")
    public ResponseEntity<List<UserBet>> getAllUserBets(
            @AuthenticationPrincipal CustomUserDetails principal){
        List<UserBet> userBets = bettingService.getAllMyBets(principal.getUserId());

        return ResponseEntity.ok(userBets);
    }
}
