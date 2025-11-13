package org.sejongisc.backend.betting.controller;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.betting.entity.Scope;
import org.sejongisc.backend.betting.service.BettingService;
import org.sejongisc.backend.betting.service.BettingScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/bet-rounds")
public class BettingAdminController {

    private final BettingService bettingService;

    @PostMapping("/daily/open")
    public ResponseEntity<String> createDailyRound() {
        bettingService.createBetRound(Scope.DAILY);
        return ResponseEntity.ok("DAILY 라운드 생성");
    }

    @PostMapping("/weekly/open")
    public ResponseEntity<String> createWeeklyRound() {
        bettingService.createBetRound(Scope.WEEKLY);
        return ResponseEntity.ok("WEEKLY 라운드 생성");
    }

    @PostMapping("/close")
    public ResponseEntity<String> closeRounds() {
        bettingService.closeBetRound();
        return ResponseEntity.ok("라운드 종료");
    }

    @PostMapping("/settle")
    public ResponseEntity<String> settleRounds() {
        bettingService.settleUserBets();
        return ResponseEntity.ok("정산");
    }
}
