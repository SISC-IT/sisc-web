package org.sejongisc.backend.betting.controller;


import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.betting.entity.BetRound;
import org.sejongisc.backend.betting.entity.Scope;
import org.sejongisc.backend.betting.service.BettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api")
public class BettingController {

    private final BettingService bettingService;

    @GetMapping("/bet-round/{scope}")
    public ResponseEntity<BetRound> getTodayBetRound(
            @PathVariable @Pattern(regexp = "daily|weekly") String scope){

        Scope scopeEnum = Scope.valueOf(scope.toUpperCase());

        Optional<BetRound> betRound = bettingService.getActiveRound(scopeEnum);

        return betRound
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/bet-round/history")
    public ResponseEntity<List<BetRound>> getAllBetRounds(){
        List<BetRound> betRounds = bettingService.getAllBetRounds();

        return ResponseEntity.ok(betRounds);
    }
}
