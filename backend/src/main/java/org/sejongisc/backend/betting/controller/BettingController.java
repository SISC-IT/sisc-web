package org.sejongisc.backend.betting.controller;


import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.betting.entity.BetRound;
import org.sejongisc.backend.betting.entity.Scope;
import org.sejongisc.backend.betting.service.BettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BettingController {

    private final BettingService bettingService;

    @GetMapping("bet-round/daily")
    public ResponseEntity<BetRound> getTodayDailyBetRound(){
        Optional<BetRound> betRound = bettingService.getActiveRound(Scope.DAILY);

        return betRound
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("bet-round/weekly")
    public ResponseEntity<BetRound> getTodayWeeklyBetRound(){
        Optional<BetRound> betRound = bettingService.getActiveRound(Scope.WEEKLY);

        return betRound
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("bet-round/history")
    public ResponseEntity<List<BetRound>> getAllBetRounds(){
        List<BetRound> betRounds = bettingService.getAllBetRounds();

        return ResponseEntity.ok(betRounds);
    }
}
