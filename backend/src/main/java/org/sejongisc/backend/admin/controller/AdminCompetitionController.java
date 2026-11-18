package org.sejongisc.backend.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.competition.dto.CompetitionCreateRequest;
import org.sejongisc.backend.competition.dto.CompetitionCreateResponse;
import org.sejongisc.backend.competition.service.CompetitionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/competitions")
@Tag(name = "00. 관리자 수익률 대결 API", description = "운영진용 수익률 대결 대회 관리 API")
public class AdminCompetitionController {

  private final CompetitionService competitionService;

  @Operation(
      summary = "대회 생성",
      description = """
      대회를 시작 전(READY) 상태로 만들고, 활동 회원 전원을 참가자로 등록해 계좌를 만듭니다. \s
      - 승인 대기 회원은 활동 상태여도 참가자로 등록하지 않습니다. \s
      - 초기 자금 1천만 원, 수수료율 0.01%, 예금 이율 연 3%가 기본값으로 저장됩니다. \s
      - 시작 전이거나 진행 중인 대회가 있으면 만들 수 없습니다. \s
      - 팀이 배정되지 않은 활동 회원이 있으면 만들 수 없습니다.
      """
  )
  @PostMapping
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<CompetitionCreateResponse> create(@Valid @RequestBody CompetitionCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(competitionService.create(request));
  }
}
