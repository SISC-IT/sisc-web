package org.sejongisc.backend.backtest.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(
    name = "백테스팅 API",
    description = "백테스팅 관련 API 제공"
)
@RequiredArgsConstructor
public class BacktestController {
  private final BacktestService backtestService;

  // 백테스트 실행 상태 조회
  @GetMapping("/runs/{backtestRunId}/status")
  @Operation(
      summary = "백테스트 실행 상태 조회",
      description = "지정된 백테스트 실행 ID에 대한 현재 상태를 조회합니다."
  )
  public ResponseEntity<BacktestResponse> getBacktestStatus(@PathVariable Long backtestRunId,
                                                            @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(backtestService.getBacktestStatus(backtestRunId, customUserDetails.getUserId()));
  }

  // 백테스트 기록 상세 조회
  @GetMapping("/runs/{backtestRunId}")
  @Operation(
      summary = "백테스트 실행 기록 상세 조회",
      description = "지정된 백테스트 실행 ID에 대한 상세 결과를 조회합니다."
  )
  public ResponseEntity<BacktestResponse> getBackTestResultDetails(@PathVariable Long backtestRunId,
                                                                   @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(backtestService.getBackTestDetails(backtestRunId, customUserDetails.getUserId()));
  }

  // 백테스트 실행
  @PostMapping("/runs")
  @Operation(
      summary = "백테스트 실행",
      description = "사용자가 요청한 전략을 기반으로 백테스트를 실행합니다."
  )
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "백테스트 실행을 위한 기본 정보 및 전략",
      required = true,
      content = @Content(
          mediaType = "application/json",
          schema = @Schema(implementation = BacktestRequest.class),
          examples = {
              @ExampleObject(
                  summary = "SMA 골든크로스 및 RSI 필터 전략 예시",
                  value = """
                {
                  "title": "골든크로스 + RSI 필터 (AAPL)",
                  "startDate": "2023-01-01",
                  "endDate": "2024-12-31",
                  "templateId": null,
                  "strategy": {
                    "initialCapital": 100000.00,
                    "ticker": "AAPL",
                    "buyConditions": [
                      {
                        "leftOperand": {
                          "type": "indicator",
                          "indicatorCode": "RSI",
                          "output": "value",
                          "params": {
                            "length": 14
                          }
                        },
                        "operator": "LT",
                        "rightOperand": {
                          "type": "const",
                          "constantValue": 30
                        },
                        "isAbsolute": true
                      },
                      {
                        "leftOperand": {
                          "type": "indicator",
                          "indicatorCode": "SMA",
                          "output": "value",
                          "params": {
                            "length": 50
                          }
                        },
                        "operator": "CROSSES_ABOVE",
                        "rightOperand": {
                          "type": "indicator",
                          "indicatorCode": "SMA",
                          "output": "value",
                          "params": {
                            "length": 200
                          }
                        },
                        "isAbsolute": false
                      },
                      {
                        "leftOperand": {
                          "type": "price",
                          "priceField": "Close"
                        },
                        "operator": "GTE",
                        "rightOperand": {
                          "type": "indicator",
                          "indicatorCode": "SMA",
                          "output": "value",
                          "params": {
                            "length": 200
                          }
                        },
                        "isAbsolute": false
                      }
                    ],
                    "sellConditions": [
                      {
                        "leftOperand": {
                          "type": "indicator",
                          "indicatorCode": "RSI",
                          "output": "value",
                          "params": {
                            "length": 14
                          }
                        },
                        "operator": "GT",
                        "rightOperand": {
                          "type": "const",
                          "constantValue": 70
                        },
                        "isAbsolute": true
                      },
                      {
                        "leftOperand": {
                          "type": "indicator",
                          "indicatorCode": "SMA",
                          "output": "value",
                          "params": {
                            "length": 50
                          }
                        },
                        "operator": "CROSSES_BELOW",
                        "rightOperand": {
                          "type": "indicator",
                          "indicatorCode": "SMA",
                          "output": "value",
                          "params": {
                            "length": 200
                          }
                        },
                        "isAbsolute": false
                      }
                    ],
                    "note": "간단한 골든크로스 전략 테스트. RSI 과매수/과매도 시 우선 청산/진입."
                  }
                }
                """
              )
          }
      )
  )
  public ResponseEntity<BacktestResponse> runBacktest(@RequestBody BacktestRequest request,
                                                      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setUser(customUserDetails.getUser());
    return ResponseEntity.ok(backtestService.runBacktest(request));
  }

  // 백테스트 실행 정보 삭제
  @DeleteMapping("/runs/{backtestRunId}")
  @Operation(
      summary = "백테스트 실행 정보 삭제",
      description = "지정된 백테스트 실행 ID에 대한 기록을 삭제합니다."
  )
  public ResponseEntity<Void> deleteBacktest(@PathVariable Long backtestRunId,
                                             @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    backtestService.deleteBacktest(backtestRunId, customUserDetails.getUserId());
    return ResponseEntity.noContent().build();
  }

  // 백테스트를 특정 템플릿에 저장
  @PatchMapping("/runs/{backtestRunId}")
  @Operation(
      summary = "템플릿에 백테스트 저장",
      description = "지정된 백테스트를 특정 템플릿에 추가합니다."
  )
  public ResponseEntity<Void> postBacktestIntoTemplate(@RequestBody BacktestRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setUser(customUserDetails.getUser());
    backtestService.addBacktestTemplate(request);
    return ResponseEntity.ok().build();
  }

  // 특정 템플릿의 백테스트 리스트 삭제
  @DeleteMapping("/templates/{templateId}/runs")
  @Operation(
      summary = "템플릿의 백테스트 삭제",
      description = "지정된 템플릿에서 특정 백테스트를 삭제합니다."
  )
  public ResponseEntity<Void> deleteBacktestFromTemplate(@RequestBody BacktestRequest request,
                                                         @PathVariable UUID templateId,
                                                         @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setUser(customUserDetails.getUser());
    backtestService.deleteBacktestFromTemplate(request, templateId);
    return ResponseEntity.noContent().build();
  }
}
