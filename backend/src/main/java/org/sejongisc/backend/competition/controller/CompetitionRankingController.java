package org.sejongisc.backend.competition.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.competition.dto.CompetitionRankingResponse;
import org.sejongisc.backend.competition.dto.CompetitionTeamRankingResponse;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.competition.service.CompetitionRankingService;
import org.sejongisc.backend.user.entity.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/competitions/{competitionId}/rankings")
@Tag(name = "수익률 대결 순위 API", description = "수익률 대결 순위 조회 API")
public class CompetitionRankingController {

    private static final Set<Role> ADMIN_ROLES = Set.of(Role.VICE_PRESIDENT, Role.PRESIDENT, Role.SYSTEM_ADMIN);

    private final CompetitionRankingService rankingService;

    @Operation(
            summary = "개인 순위 조회",
            description = """
            가장 최근 거래일 종가로 계산한 개인 순위를 등수 순으로 반환합니다. \s
            - 순위는 거래일마다 종가 수집 직후 한 번 계산합니다. 그날 저녁 거래의 수수료는 다음 거래일 순위에 반영됩니다. \s
            - rankChange는 직전 거래일 대비 등수 변동입니다. 양수면 상승, 음수면 하락이고 비교할 기록이 없으면 null입니다. \s
            - 총자산이 같으면 같은 등수이고 다음 등수는 건너뜁니다. \s
            - profitRate는 대회 초기 자산 대비 비율입니다(0.05는 5%). \s
            - 관리자가 아니면 본인 줄만 userId, userName, teamName이 채워집니다. 다른 참가자는 userName이 "익명"이고 userId와 teamName은 null입니다. \s
            - participantId는 이번 대회 안에서만 쓰는 참가자 ID입니다. \s
            - 종료일부터 결과 공개 전까지는 관리자(부회장 이상)만 볼 수 있습니다. 그 외에는 403 COMPETITION_RESULT_NOT_PUBLIC입니다. \s
            - 계산된 순위가 아직 없으면 snapshotDate는 null이고 rankings는 빈 목록입니다.
            """
    )
    @GetMapping
    public ResponseEntity<CompetitionRankingResponse> getRankings(
            @PathVariable UUID competitionId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(rankingService.getRankings(competitionId, viewerId(userDetails), isAdmin(userDetails)));
    }

    @Operation(
            summary = "팀 순위 조회",
            description = """
            개인 순위와 같은 스냅샷으로 계산한 팀 순위를 등수 순으로 반환합니다. \s
            - 팀원 수가 달라도 공정하도록 팀원 개인 등수의 평균(averageRank)이 낮은 팀이 앞섭니다. \s
            - averageRank는 소수 둘째 자리까지 반올림한 값입니다. 등수는 반올림 전 평균으로 정합니다. \s
            - 평균 등수가 같으면 같은 등수이고 다음 등수는 건너뜁니다. \s
            - rankSum은 팀원 개인 등수의 합, memberCount는 순위에 포함된 팀원 수입니다. 제외된 참가자는 빠집니다. \s
            - 종료일부터 결과 공개 전까지는 관리자(부회장 이상)만 볼 수 있습니다. 그 외에는 403 COMPETITION_RESULT_NOT_PUBLIC입니다. \s
            - 계산된 순위가 아직 없으면 snapshotDate는 null이고 rankings는 빈 목록입니다.
            """
    )
    @GetMapping("/teams")
    public ResponseEntity<CompetitionTeamRankingResponse> getTeamRankings(
            @PathVariable UUID competitionId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(rankingService.getTeamRankings(competitionId, isAdmin(userDetails)));
    }

    private static UUID viewerId(CustomUserDetails userDetails) {
        return userDetails == null ? null : userDetails.getUserId();
    }

    private static boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails != null && ADMIN_ROLES.contains(userDetails.getRole());
    }
}
