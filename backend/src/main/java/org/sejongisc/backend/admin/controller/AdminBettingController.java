package org.sejongisc.backend.admin.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.betting.entity.Scope;
import org.sejongisc.backend.betting.service.BettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/bet-rounds")
@Tag(
        name = "00. 관리자 모의 트레이딩 관리 API",
        description = "모의 트레이딩 관리 관련 API 제공"
)
public class AdminBettingController {

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
