package org.sejongisc.backend.point.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(
    name = "포인트 내역 및 리더보드 API",
    description = "포인트 내역 조회 및 리더보드 관련 API 제공"
)
public class PointHistoryController {

  private final PointHistoryService pointHistoryService;

  @GetMapping("/history")
  @Operation(
      summary = "포인트 내역 조회",
      description = "인증된 사용자의 포인트 내역을 페이지네이션하여 조회합니다."
  )
  public ResponseEntity<PointHistoryResponse> getPointHistory(@RequestParam int pageNumber, @RequestParam int pageSize,
                                                              @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(pointHistoryService.getPointHistoryListByUserId(
        customUserDetails.getUserId(), PageRequest.of(pageNumber, pageSize))
    );
  }

  @GetMapping("/leaderboard")
  @Operation(
      summary = "포인트 리더보드 조회",
      description = "지정된 기간 동안의 포인트 리더보드를 조회합니다. 기간은 일간, 주간, 월간 단위의 요청이 가능합니다. ex) /leaderboard?period=1 or 7 or 30"
  )
  public ResponseEntity<PointHistoryResponse> getPointLeaderboard(@RequestParam int period) {
    return ResponseEntity.ok(pointHistoryService.getPointLeaderboard(period));
  }
}
