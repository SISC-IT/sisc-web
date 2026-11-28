package org.sejongisc.backend.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.competition.dto.CompetitionCreateRequest;
import org.sejongisc.backend.competition.dto.CompetitionCreateResponse;
import org.sejongisc.backend.competition.dto.CompetitionRankingResponse;
import org.sejongisc.backend.competition.dto.CompetitionTeamRankingResponse;
import org.sejongisc.backend.competition.service.CompetitionRankingService;
import org.sejongisc.backend.competition.service.CompetitionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/competitions")
@Tag(name = "00. 관리자 수익률 대결 API", description = "운영진용 수익률 대결 대회 관리 API")
public class AdminCompetitionController {

  private final CompetitionService competitionService;
  private final CompetitionRankingService rankingService;

  @Operation(
      summary = "대회 생성",
      description = """
      대회를 시작 전(READY) 상태로 만들고(시작일이 오늘이면 바로 진행 중(ACTIVE)), 활동 회원 전원을 참가자로 등록해 계좌를 만듭니다. \s
      - 승인 대기 회원은 활동 상태여도 참가자로 등록하지 않습니다. \s
      - 초기 자금 1천만 원, 수수료율 0.01%, 예금 이율 연 3%가 기본값으로 저장됩니다. \s
      - 종료일은 시작일보다 늦어야 합니다. 종료일에는 주문을 받지 않고 그날 종가로 최종 평가합니다. \s
      - 시작 전이거나 진행 중인 대회가 있으면 만들 수 없습니다. \s
      - 팀이 배정되지 않은 활동 회원이 있으면 만들 수 없습니다. \s
      - 첫날부터 주문하려면 시작 전날까지 만들어야 합니다. 거래 종목과 종가는 평일 오후에 하루 한 번 준비되어, 시작 당일 오후에 만들면 그날은 주문할 수 없습니다.
      """
  )
  @PostMapping
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<CompetitionCreateResponse> create(@Valid @RequestBody CompetitionCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(competitionService.create(request));
  }

  @Operation(
      summary = "대회 결과 공개",
      description = """
      종료(ENDED)된 대회를 결과 공개(FINALIZED) 상태로 바꿉니다. \s
      - 대회는 종료일 16:00 종가로 최종 등수를 정하고 자동으로 종료됩니다. 운영진이 결과를 확인한 뒤 이 API로 공개합니다. \s
      - 종료 상태가 아닌 대회는 409 COMPETITION_NOT_ENDED입니다. 이미 공개한 대회도 마찬가지입니다.
      """
  )
  @PostMapping("/{competitionId}/finalize")
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<Void> finalizeResult(@PathVariable UUID competitionId) {
    competitionService.finalizeResult(competitionId);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "개인 순위 조회",
      description = """
      일반 개인 순위 API와 같은 순위를 모든 참가자의 실명과 userId를 담아 반환합니다. \s
      - 종료일부터 결과 공개 전까지도 볼 수 있어, 공개 전에 결과를 검토할 때 씁니다. \s
      - 계산된 순위가 아직 없으면 snapshotDate는 null이고 rankings는 빈 목록입니다.
      """
  )
  @GetMapping("/{competitionId}/rankings")
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<CompetitionRankingResponse> getRankings(@PathVariable UUID competitionId) {
    return ResponseEntity.ok(rankingService.getAdminRankings(competitionId));
  }

  @Operation(
      summary = "팀 순위 조회",
      description = """
      일반 팀 순위 API와 같은 팀 순위를 반환합니다. \s
      - 종료일부터 결과 공개 전까지도 볼 수 있어, 공개 전에 결과를 검토할 때 씁니다. \s
      - 계산된 순위가 아직 없으면 snapshotDate는 null이고 rankings는 빈 목록입니다.
      """
  )
  @GetMapping("/{competitionId}/rankings/teams")
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<CompetitionTeamRankingResponse> getTeamRankings(@PathVariable UUID competitionId) {
    return ResponseEntity.ok(rankingService.getAdminTeamRankings(competitionId));
  }
}
