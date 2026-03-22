package org.sejongisc.backend.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.activity.entity.ActivityLog;
import org.sejongisc.backend.admin.dto.dashboard.BoardActivityResponse;
import org.sejongisc.backend.admin.dto.dashboard.RoleDistributionResponse;
import org.sejongisc.backend.admin.dto.dashboard.SummaryResponse;
import org.sejongisc.backend.admin.dto.dashboard.VisitorTrendResponse;
import org.sejongisc.backend.admin.service.AdminDashboardService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/dashboard")
@Tag(name = "00. 관리자 대시보드 API", description = "대시보드 통계 및 실시간 로그 API")
public class AdminDashboardController {

  private final AdminDashboardService adminDashboardService;

  // --- [통계 요약 섹션] ---

  @Operation(summary = "주간 게시판 활동 요약", description = "이번 주(일~현재) 게시글 활동 수와 전주 대비 증감률(%)을 반환합니다.")
  @GetMapping("/stats/boards/summary")
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<SummaryResponse> getWeeklyBoardSummary() {
    return ResponseEntity.ok(adminDashboardService.getWeeklyBoardSummary());
  }

  @Operation(summary = "주간 방문자 요약", description = "이번 주(일~현재) 누적 방문자 수와 전주 대비 증감률(%)을 반환합니다.")
  @GetMapping("/stats/visitors/summary")
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<SummaryResponse> getWeeklyVisitorSummary() {
    return ResponseEntity.ok(adminDashboardService.getWeeklyVisitorSummary());
  }

  // --- [차트 데이터 섹션] ---

  @Operation(summary = "방문자 추이 데이터", description = "최근 n일간의 일별 방문자 수 데이터를 반환합니다.")
  @GetMapping("/stats/visitors/trend")
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<List<VisitorTrendResponse>> getVisitorTrend(@RequestParam(defaultValue = "7") int days) {
    return ResponseEntity.ok(adminDashboardService.getVisitorTrend(days));
  }

  @Operation(summary = "게시판별 활동 분포", description = "최근 n일간의 게시판별 활동량(게시글+댓글+좋아요) 집계를 반환합니다.")
  @GetMapping("/stats/boards/distribution")
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<List<BoardActivityResponse>> getBoardActivityStats(@RequestParam(defaultValue = "7") int days) {
    return ResponseEntity.ok(adminDashboardService.getBoardActivityStats(days));
  }

  @Operation(summary = "회원 권한 분포", description = "현재 기준 활성 사용자들의 회원 권한 분포 데이터를 반환합니다.")
  @GetMapping("/stats/users/distribution")
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<List<RoleDistributionResponse>> getRoleDistributionStats() {
    return ResponseEntity.ok(adminDashboardService.getRoleDistributionStats());
  }

  // --- [활동 로그 섹션] ---

  @Operation(summary = "실시간 활동 로그 스트림 (SSE)", description = "관리자용 실시간 활동 로그 구독 세션을 엽니다.")
  @GetMapping(value = "/activities/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<SseEmitter> streamRealtimeLog() {
    return ResponseEntity.ok(adminDashboardService.subscribeActivityStream());
  }

  @Operation(summary = "최근 활동 로그 목록 (페이징)", description = "대시보드 하단 로그를 최신순으로 조회합니다. hasNext 필드로 무한 스크롤을 구현하세요.")
  @GetMapping("/activities")
  @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
  public ResponseEntity<Slice<ActivityLog>> getRecentActivities(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
  ) {
    return ResponseEntity.ok(adminDashboardService.getRecentActivities(pageable));
  }
}
