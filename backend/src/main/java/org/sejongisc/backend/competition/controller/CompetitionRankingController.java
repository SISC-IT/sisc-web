package org.sejongisc.backend.competition.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.competition.dto.CompetitionRankingResponse;
import org.sejongisc.backend.competition.service.CompetitionRankingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/competitions/{competitionId}/rankings")
@Tag(name = "수익률 대결 순위 API", description = "수익률 대결 순위 조회 API")
public class CompetitionRankingController {

    private final CompetitionRankingService rankingService;

    @Operation(
            summary = "개인 순위 조회",
            description = """
            가장 최근 거래일 종가로 계산한 개인 순위를 등수 순으로 반환합니다. \s
            - 순위는 거래일마다 종가 수집 직후 한 번 계산합니다. 그날 저녁 거래의 수수료는 다음 거래일 순위에 반영됩니다. \s
            - rankChange는 직전 거래일 대비 등수 변동입니다. 양수면 상승, 음수면 하락이고 비교할 기록이 없으면 null입니다. \s
            - 총자산이 같으면 같은 등수이고 다음 등수는 건너뜁니다. \s
            - profitRate는 대회 초기 자산 대비 비율입니다(0.05는 5%). \s
            - 계산된 순위가 아직 없으면 snapshotDate는 null이고 rankings는 빈 목록입니다.
            """
    )
    @GetMapping
    public ResponseEntity<CompetitionRankingResponse> getRankings(@PathVariable UUID competitionId) {
        return ResponseEntity.ok(rankingService.getRankings(competitionId));
    }
}
