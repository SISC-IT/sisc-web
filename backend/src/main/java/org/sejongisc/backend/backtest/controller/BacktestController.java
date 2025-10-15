package org.sejongisc.backend.backtest.controller;


import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.backtest.dto.BacktestRequest;
import org.sejongisc.backend.backtest.dto.BacktestResponse;
import org.sejongisc.backend.backtest.service.BacktestService;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
public class BacktestController {
  private final BacktestService backtestService;

  // 백테스트 실행 상태 조회
  @GetMapping("/runs/{backtestRunId}/status")
  public ResponseEntity<BacktestResponse> getBacktestStatus(@PathVariable Long backtestRunId,
                                                            @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(backtestService.getBacktestStatus(backtestRunId, customUserDetails.getUserId()));
  }

  // 백테스트 기록 상세 조회
  @GetMapping("/runs/{backtestRunId}")
  public ResponseEntity<BacktestResponse> getBackTestResultDetails(@PathVariable Long backtestRunId,
                                                                   @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(backtestService.getBackTestDetails(backtestRunId, customUserDetails.getUserId()));
  }

  // 백테스트 실행
  @PostMapping("/runs")
  public ResponseEntity<BacktestResponse> runBacktest(@RequestBody BacktestRequest request,
                                                      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setUserId(customUserDetails.getUserId());
    return ResponseEntity.ok(backtestService.runBacktest(request));
  }

  // 백테스트 실행 정보 삭제
  @DeleteMapping("/runs/{backtestRunId}")
  public ResponseEntity<Void> deleteBacktest(@PathVariable Long backtestRunId,
                                             @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    backtestService.deleteBacktest(backtestRunId, customUserDetails.getUserId());
    return ResponseEntity.noContent().build();
  }

  // 백테스트를 특정 템플릿에 저장
  @PatchMapping("/runs/{backtestRunId}")
  public ResponseEntity<Void> postBacktestIntoTemplate(@RequestBody BacktestRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setUserId(customUserDetails.getUserId());
    backtestService.addBacktestTemplate(request);
    return ResponseEntity.ok().build();
  }

  // 특정 템플릿의 백테스트 리스트 삭제
  @DeleteMapping("/templates/{templateId}/runs")
  public ResponseEntity<Void> deleteBacktestFromTemplate(@RequestBody BacktestRequest request,
                                                         @PathVariable UUID templateId,
                                                         @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setUserId(customUserDetails.getUserId());
    backtestService.deleteBacktestFromTemplate(request, templateId);
    return ResponseEntity.noContent().build();
  }
}
