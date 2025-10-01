package org.sejongisc.backend.point.controller;


import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.point.dto.PointHistoryResponse;
import org.sejongisc.backend.point.service.PointHistoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointHistoryController {

  private final PointHistoryService pointHistoryService;

  @GetMapping("/history")
  public ResponseEntity<PointHistoryResponse> getPointHistory(
      @RequestParam int pageNumber,
      @RequestParam int pageSize,
      @AuthenticationPrincipal CustomUserDetails customUserDetails
  ) {
    return ResponseEntity.ok(pointHistoryService.getPointHistoryListByUserId(
        customUserDetails.getUserId(), PageRequest.of(pageNumber, pageSize))
    );
  }

  @GetMapping("/leaderboard")
  public ResponseEntity<PointHistoryResponse> getPointLeaderboard(
      @RequestParam int period,
      @RequestParam int limit
  ) {
    return ResponseEntity.ok(pointHistoryService.getPointLeaderboard(period, limit));
  }
}
