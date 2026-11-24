package org.sejongisc.backend.competition.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.competition.dto.CompetitionResponse;
import org.sejongisc.backend.competition.service.CompetitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/competitions")
@Tag(name = "수익률 대결 대회 API", description = "수익률 대결 대회 조회 API")
public class CompetitionController {

    private final CompetitionService competitionService;

    @Operation(
            summary = "현재 대회 조회",
            description = """
            화면에 보여줄 대회 하나를 반환합니다. 시작 전·진행 중인 대회가 있으면 그 대회이고, 없으면 가장 최근에 끝난 대회입니다. \s
            - status: READY(시작 전), ACTIVE(진행 중), ENDED(종료, 결과 비공개), FINALIZED(결과 공개) \s
            - myStatus: 로그인한 사용자의 참가 상태입니다. ACTIVE(참가 중), EXCLUDED(제외), 참가자가 아니면 null입니다. \s
            - 대회가 한 번도 없으면 404 COMPETITION_NOT_FOUND입니다.
            """
    )
    @GetMapping("/current")
    public ResponseEntity<CompetitionResponse> getCurrent(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(competitionService.getCurrent(userDetails.getUserId()));
    }

    @Operation(
            summary = "대회 조회",
            description = """
            대회 기본 정보와 로그인한 사용자의 참가 상태(myStatus)를 반환합니다. 형식은 현재 대회 조회와 같습니다. \s
            - 대회가 없으면 404 COMPETITION_NOT_FOUND입니다.
            """
    )
    @GetMapping("/{competitionId}")
    public ResponseEntity<CompetitionResponse> get(
            @PathVariable UUID competitionId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(competitionService.get(competitionId, userDetails.getUserId()));
    }
}
