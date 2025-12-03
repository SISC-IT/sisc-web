package org.sejongisc.backend.stock.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.stock.dto.HoldingDto;
import org.sejongisc.backend.stock.dto.PositionDto;
import org.sejongisc.backend.stock.dto.TradeLogDto;
import org.sejongisc.backend.stock.dto.XaiReportResponse;
import org.sejongisc.backend.stock.entity.Execution;
import org.sejongisc.backend.stock.entity.XaiReport;
import org.sejongisc.backend.stock.repository.ExecutionRepository;
import org.sejongisc.backend.stock.repository.XaiReportRepository;
import org.sejongisc.backend.stock.repository.projection.PortfolioOverviewProjection;
import org.sejongisc.backend.stock.repository.projection.PortfolioSimpleProjection;
import org.sejongisc.backend.stock.repository.projection.PositionProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuantBotService {

  private final ExecutionRepository executionRepository;
  private final XaiReportRepository xaiReportRepository;

  // 매매 로그
  public List<TradeLogDto> getTradeLogs() {

    return executionRepository.findAllByOrderByFillDateDesc();
  }

  // 종목별 보유 현황
  public List<HoldingDto> getHoldings() {

    return executionRepository.findCurrentHoldings();
  }

  // execution에 따른 리포트 조회
  @Transactional(readOnly = true)
  public XaiReportResponse getXaiReportByExecutionId(Long executionId) {
    Execution exec = executionRepository.findWithXaiReportById(executionId)
        .orElseThrow(() -> new CustomException(ErrorCode.EXECUTION_NOT_FOUND));

    XaiReport xaiReport = exec.getXaiReport();
    if(xaiReport == null) {
      throw new CustomException(ErrorCode.XAI_REPORT_NOT_FOUND);
    }

    return XaiReportResponse.from(xaiReport);
  }

  //현재 자산 변화 기록 조회
  @Transactional(readOnly = true)
  public List<PortfolioSimpleProjection> getAssets(){
    return executionRepository.findSimpleSummary();
  }

  public List<PositionDto> getPositions() {
    List<PositionProjection> rows = executionRepository.findAllPositions();

    return rows.stream()
            .map(p -> new PositionDto(
                    p.getTicker(),
                    p.getPositionQty(),
                    p.getAvgPrice(),
                    p.getCurrentPrice(),
                    p.getMarketPrice(),   // marketPrice 계산됨
                    null,   // pnl
                    null    // pnlRate
            ).setPnl()) // 계산 자동 수행
            .toList();
  }

  public PortfolioOverviewProjection getPortfolioOverview() {
    return executionRepository.getPortfolioOverview();
  }
}

