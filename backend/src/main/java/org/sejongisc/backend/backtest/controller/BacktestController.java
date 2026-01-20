package org.sejongisc.backend.backtest.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.sejongisc.backend.backtest.dto.BacktestRequest;
import org.sejongisc.backend.backtest.dto.BacktestResponse;
import org.sejongisc.backend.backtest.dto.BacktestRunRequest;
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

  @GetMapping("/stocks/info")
  @Operation(
      summary = "백테스트용 주식 정보 조회",
      description = "백테스트에 사용되는 주식의 기본 정보를 조회합니다."
  )
  public ResponseEntity<BacktestResponse> getBacktestStockInfo() {
    return ResponseEntity.ok(backtestService.getBacktestStockInfo());
  }

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
          summary = "백테스팅 실행",
          description = "다양한 보조지표를 조합하여 비동기 백테스팅을 실행합니다. 아래 Examples에서 원하는 전략을 선택하여 테스트하세요.",
          requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                  content = @Content(
                          schema = @Schema(implementation = BacktestRequest.class),
                          examples = {
                                  @ExampleObject(
                                          name = "1. [추세] 이동평균 골든크로스 (SMA)",
                                          description = "단기 이평선이 장기 이평선을 돌파할 때 매수합니다.",
                                          value = """
                            {
                              "title": "SMA 5일/20일 골든크로스",
                              "startDate": "2023-01-01",
                              "endDate": "2023-12-31",
                              "strategy": {
                                "ticker": "AAPL",
                                "initialCapital": 10000000,
                                "defaultExitDays": 0,
                                "buyConditions": [
                                  {
                                    "leftOperand": { "type": "indicator", "indicatorCode": "SMA", "params": { "length": 5 } },
                                    "operator": "CROSSES_ABOVE",
                                    "rightOperand": { "type": "indicator", "indicatorCode": "SMA", "params": { "length": 20 } },
                                    "isAbsolute": true
                                  }
                                ],
                                "sellConditions": [
                                  {
                                    "leftOperand": { "type": "indicator", "indicatorCode": "SMA", "params": { "length": 5 } },
                                    "operator": "CROSSES_BELOW",
                                    "rightOperand": { "type": "indicator", "indicatorCode": "SMA", "params": { "length": 20 } },
                                    "isAbsolute": true
                                  }
                                ]
                              }
                            }
                            """
                                  ),
                                  @ExampleObject(
                                          name = "2. [모멘텀] RSI & MACD 조합",
                                          description = "RSI 과매도 + MACD 상승 반전 시 매수",
                                          value = """
                            {
                              "title": "RSI + MACD 복합 전략",
                              "startDate": "2023-01-01",
                              "endDate": "2023-12-31",
                              "strategy": {
                                "ticker": "TSLA",
                                "initialCapital": 10000000,
                                "defaultExitDays": 0,
                                "buyConditions": [
                                  {
                                    "leftOperand": { "type": "indicator", "indicatorCode": "RSI", "params": { "length": 14 } },
                                    "operator": "LT",
                                    "rightOperand": { "type": "const", "constantValue": 40 },
                                    "isAbsolute": false
                                  },
                                  {
                                    "leftOperand": { "type": "indicator", "indicatorCode": "MACD", "output": "macd", "params": { "fast": 12, "slow": 26, "signal": 9 } },
                                    "operator": "GT",
                                    "rightOperand": { "type": "indicator", "indicatorCode": "MACD", "output": "signal", "params": { "fast": 12, "slow": 26, "signal": 9 } },
                                    "isAbsolute": false
                                  }
                                ],
                                "sellConditions": [
                                  {
                                    "leftOperand": { "type": "indicator", "indicatorCode": "RSI", "params": { "length": 14 } },
                                    "operator": "GT",
                                    "rightOperand": { "type": "const", "constantValue": 70 },
                                    "isAbsolute": true
                                  }
                                ]
                              }
                            }
                            """
                                  ),
                                  @ExampleObject(
                                          name = "3. [변동성] 볼린저 밴드 (Bollinger Bands)",
                                          description = "하단 밴드 터치 시 매수, 상단 밴드 터치 시 매도",
                                          value = """
                            {
                              "title": "볼린저 밴드 역추세",
                              "startDate": "2023-01-01",
                              "endDate": "2023-12-31",
                              "strategy": {
                                "ticker": "MSFT",
                                "initialCapital": 5000000,
                                "defaultExitDays": 0,
                                "buyConditions": [
                                  {
                                    "leftOperand": { "type": "price", "priceField": "Close" },
                                    "operator": "LT",
                                    "rightOperand": { "type": "indicator", "indicatorCode": "BB", "output": "lower", "params": { "length": 20, "k": 2.0 } },
                                    "isAbsolute": true
                                  }
                                ],
                                "sellConditions": [
                                  {
                                    "leftOperand": { "type": "price", "priceField": "Close" },
                                    "operator": "GT",
                                    "rightOperand": { "type": "indicator", "indicatorCode": "BB", "output": "upper", "params": { "length": 20, "k": 2.0 } },
                                    "isAbsolute": true
                                  }
                                ]
                              }
                            }
                            """
                                  ),
                                  @ExampleObject(
                                          name = "4. [오실레이터] 스토캐스틱 & CCI",
                                          description = "스토캐스틱 골든크로스 & CCI 과매수 청산",
                                          value = """
                            {
                              "title": "Stochastic & CCI 전략",
                              "startDate": "2023-01-01",
                              "endDate": "2023-12-31",
                              "strategy": {
                                "ticker": "NVDA",
                                "initialCapital": 10000000,
                                "defaultExitDays": 0,
                                "buyConditions": [
                                  {
                                    "leftOperand": { "type": "indicator", "indicatorCode": "STOCH", "output": "k", "params": { "kLength": 14, "dLength": 3 } },
                                    "operator": "CROSSES_ABOVE",
                                    "rightOperand": { "type": "const", "constantValue": 20 },
                                    "isAbsolute": true
                                  }
                                ],
                                "sellConditions": [
                                  {
                                    "leftOperand": { "type": "indicator", "indicatorCode": "CCI", "params": { "length": 14 } },
                                    "operator": "GT",
                                    "rightOperand": { "type": "const", "constantValue": 100 },
                                    "isAbsolute": true
                                  }
                                ]
                              }
                            }
                            """
                                  ),
                                  @ExampleObject(
                                          name = "5. [추세강도] ADX & ATR (고급)",
                                          description = "ADX로 추세 확인 후 EMA 돌파 매매",
                                          value = """
                            {
                              "title": "ADX 추세 추종 & ATR 활용",
                              "startDate": "2023-01-01",
                              "endDate": "2023-12-31",
                              "strategy": {
                                "ticker": "AMD",
                                "initialCapital": 10000000,
                                "defaultExitDays": 30,
                                "buyConditions": [
                                  {
                                    "leftOperand": { "type": "indicator", "indicatorCode": "ADX", "params": { "length": 14 } },
                                    "operator": "GT",
                                    "rightOperand": { "type": "const", "constantValue": 25 },
                                    "isAbsolute": false
                                  },
                                  {
                                    "leftOperand": { "type": "price", "priceField": "Close" },
                                    "operator": "GT",
                                    "rightOperand": { "type": "indicator", "indicatorCode": "EMA", "params": { "length": 20 } },
                                    "isAbsolute": false
                                  }
                                ],
                                "sellConditions": [
                                  {
                                    "leftOperand": { "type": "price", "priceField": "Close" },
                                    "operator": "LT",
                                    "rightOperand": { "type": "indicator", "indicatorCode": "EMA", "params": { "length": 20 } },
                                    "isAbsolute": true
                                  }
                                ]
                              }
                            }
                            """
                                  )
                          }
                  )
          )
  )
  public ResponseEntity<BacktestResponse> runBacktest(@org.springframework.web.bind.annotation.RequestBody BacktestRequest request, //
                                                      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setUserId(customUserDetails.getUserId()); // 사용자 ID 주입
    BacktestResponse response = backtestService.runBacktest(request);
    return ResponseEntity.ok(response);
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
    request.setUserId(customUserDetails.getUserId());
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
    request.setUserId(customUserDetails.getUserId());
    backtestService.deleteBacktestFromTemplate(request, templateId);
    return ResponseEntity.noContent().build();
  }
}
