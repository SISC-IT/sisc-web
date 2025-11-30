package org.sejongisc.backend.stock.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.stock.dto.HoldingDto;
import org.sejongisc.backend.stock.dto.PositionDto;
import org.sejongisc.backend.stock.dto.TradeLogDto;
import org.sejongisc.backend.stock.dto.XaiReportResponse;
import org.sejongisc.backend.stock.entity.XaiReport;
import org.sejongisc.backend.stock.repository.projection.PortfolioOverviewProjection;
import org.sejongisc.backend.stock.repository.projection.PortfolioSimpleProjection;
import org.sejongisc.backend.stock.service.QuantBotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quant-bot")
@RequiredArgsConstructor
public class QuantBotController {

  private final QuantBotService quantBotService;

  // 매매 로그
  @Operation(
      summary = "매매 로그 조회",
      description = """
          ## 인증(JWT): **필요**
          
          ## 설명
          - 퀀트 봇이 기록한 모든 매매 로그를 조회합니다.
          - 일반적으로 가장 최근 매매 내역부터 정렬된 리스트를 반환합니다.
          
          ## 요청 파라미터
          - **요청 파라미터 없음**
          
          ## 반환값 (List<TradeLogDto>)
          - **`TradeLogDto` 리스트**
          - 각 요소는 개별 매매에 대한 로그 정보를 담습니다.
          
          """
  )
  @GetMapping("/logs")
  public List<TradeLogDto> getTradeLogs(
  ) {
    return quantBotService.getTradeLogs();
  }

  // 전체 종목별 보유 현황
  @GetMapping("/holdings")
  @Operation(
      summary = "전체 보유 종목 현황 조회",
      description = """
          ## 인증(JWT): **필요**
          
          ## 설명
          - 퀀트 봇이 현재 보유 중인 **모든 종목의 포지션 현황**을 조회합니다.
          - 종목별 수량, 평균단가, 평가손익 등의 정보를 포함할 수 있습니다.
          
          ## 요청 파라미터
          - **요청 파라미터 없음**
          
          ## 반환값 (List<HoldingDto>)
          - **`HoldingDto` 리스트**
          - 각 요소는 하나의 종목에 대한 보유 현황 정보를 나타냅니다.
          
          """
  )
  public List<HoldingDto> getHoldings()
  {
    return quantBotService.getHoldings();
  }

  // 리포트 조회
  @Operation(
      summary = "XAI 리포트 조회",
      description = """
          ## 인증(JWT): **필요**
          
          ## 설명
          - 퀀트 전략 실행에 대해 생성된 **XAI(설명 가능한 AI) 리포트**를 조회합니다.
          - 특정 실행 ID를 지정하면 해당 실행에 대한 리포트를 반환하고,
            지정하지 않으면 기본 정책(예: 가장 최근 실행)에 따른 리포트를 반환합니다.
          
          ## 요청 파라미터 (QueryString)
          - **`executionId`** *(optional, Long)*: 
            - 리포트를 조회할 전략 실행 ID
            - 미제공 시, 시스템 기본값(예: 최근 실행)에 대한 리포트를 조회합니다.
          
          ## 반환값 (XaiReport)
          - **`XaiReport`**: 
            - 선택된 실행에 대한 설명 가능한 AI 리포트 객체
            
          ## 에러코드
          - **`XAI_REPORT_NOT_FOUND`**: 해당 실행 ID에 대한 리포트를 찾을 수 없습니다
          - **`QUANT_EXECUTION_NOT_FOUND`**: executionId에 해당하는 전략 실행 내역이 없습니다
          
          """
  )
  @GetMapping("/report")
  public ResponseEntity<XaiReportResponse> getAllReports(@RequestParam(required = false) Long executionId) {
    XaiReportResponse response = quantBotService.getXaiReportByExecutionId(executionId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/assets")
  @Operation(
          summary = "일별 전체 자산 변화 내역 반환",
          description = """
          ## 인증(JWT): **필요**
          
          ## 설명
          - 일별 퀀트 봇의 현재 자산+현금을 합한 내역을 반환합니다. 
          
          ## 요청 파라미터
          - **요청 파라미터 없음**
          
          ## 반환값 (List<PortfolioSimpleProjection>)
          - PortfolioSimpleProjection = (total_asset, created_at)
          """
  )
  public ResponseEntity<List<PortfolioSimpleProjection>> getAllAssets(){
    List<PortfolioSimpleProjection> assets = quantBotService.getAssets();
    if (assets.size() == 0) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(assets);
  }

  @GetMapping("/positions")
  @Operation(
          summary = "현재 포지션 목록 반환",
          description = """
          ## 인증(JWT): **필요**
          
          ## 설명
          - 현재 보유하고 있는 포지션을 반환합니다. 
          
          ## 요청 파라미터
          - **요청 파라미터 없음**
          
          ## 반환값 (List<PositionDto>)
          - PositionDto = (ticker:티커, positionQty:보유수량 ,avgPrice:매입 평균가,currentPrice:현재 주가,marketPrice:평가금액,pnl:손익금,pnlRate:손익비율)
          """
  )
  public ResponseEntity<List<PositionDto>> getPositions(){
    List<PositionDto> positions = quantBotService.getPositions();
    if (positions.size() == 0) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(positions);
  }

  @GetMapping("/portfolio-overview")
  @Operation(
          summary = "자산현황",
          description = """
          ## 인증(JWT): **필요**
          
          ## 설명
          - 현재 자산의 금액, 원금, 총 수익률, 시작, 최근 일자를 반환합니다 
          
          ## 요청 파라미터
          - **요청 파라미터 없음**
          
          ## 반환값 (List<PositionDto>)
          -     LocalDate startDate
                LocalDate endDate();
                BigDecimal lastTotalAsset();
                BigDecimal initialCapital();
          """
  )
  public PortfolioOverviewProjection getPortfolioOverview() {
    return quantBotService.getPortfolioOverview();
  }
}

