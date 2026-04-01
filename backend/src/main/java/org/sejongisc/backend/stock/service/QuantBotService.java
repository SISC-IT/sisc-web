package org.sejongisc.backend.stock.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.stock.dto.PositionDto;
import org.sejongisc.backend.stock.dto.TradeLogDto;
import org.sejongisc.backend.stock.dto.XaiReportResponse;
import org.sejongisc.backend.stock.entity.CompanyName;
import org.sejongisc.backend.stock.entity.Execution;
import org.sejongisc.backend.stock.entity.XaiReport;
import org.sejongisc.backend.stock.repository.CompanyNameRepository;
import org.sejongisc.backend.stock.repository.ExecutionRepository;
import org.sejongisc.backend.stock.repository.projection.PortfolioOverviewProjection;
import org.sejongisc.backend.stock.repository.projection.PortfolioSimpleProjection;
import org.sejongisc.backend.stock.repository.projection.PositionProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuantBotService {

  private final ExecutionRepository executionRepository;
  private final CompanyNameRepository companyNameRepository;

  // 매매 로그
  public List<TradeLogDto> getTradeLogs() {
    List<TradeLogDto> logs = executionRepository.findAllByOrderByFillDateDesc();
    Map<String, String> tickerNameMap = getTickerNameMap(
        logs.stream().map(TradeLogDto::ticker).collect(Collectors.toSet())
    );

    return logs.stream()
        .map(log -> new TradeLogDto(
            log.id(),
            log.xaiReportId(),
            log.ticker(),
            toDisplayTicker(log.ticker(), tickerNameMap),
            log.fillDate(),
            log.fillPrice(),
            log.qty(),
            log.side(),
            log.value(),
            log.positionQty(),
            log.avgPrice(),
            log.pnlRealized()
        ))
        .toList();
  }

  /*// 종목별 보유 현황
  public List<HoldingDto> getHoldings() {
    return executionRepository.findCurrentHoldings();
  }
*/
  // execution에 따른 리포트 조회
  @Transactional(readOnly = true)
  public XaiReportResponse getXaiReportByExecutionId(Long executionId) {
    Execution exec = executionRepository.findWithXaiReportById(executionId)
        .orElseThrow(() -> new CustomException(ErrorCode.EXECUTION_NOT_FOUND));

    XaiReport xaiReport = exec.getXaiReport();
    if(xaiReport == null) {
      throw new CustomException(ErrorCode.XAI_REPORT_NOT_FOUND);
    }

    String displayTicker = companyNameRepository.findByTicker(xaiReport.getTicker())
        .map(CompanyName::getCompanyName)
        .orElse(xaiReport.getTicker());

    return new XaiReportResponse(
        xaiReport.getTicker(),
        displayTicker,
        xaiReport.getSignal(),
        xaiReport.getPrice(),
        xaiReport.getDate(),
        xaiReport.getReport()
    );
  }

  //현재 자산 변화 기록 조회
  @Transactional(readOnly = true)
  public List<PortfolioSimpleProjection> getAssets(){
    return executionRepository.findSimpleSummary();
  }

  public List<PositionDto> getPositions() {
    List<PositionProjection> rows = executionRepository.findAllPositions();
    Map<String, String> tickerNameMap = getTickerNameMap(
        rows.stream().map(PositionProjection::getTicker).collect(Collectors.toSet())
    );

    return rows.stream()
            .map(p -> new PositionDto(
                    p.getTicker(),
                    toDisplayTicker(p.getTicker(), tickerNameMap),
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

  private Map<String, String> getTickerNameMap(Set<String> tickers) {
    if (tickers == null || tickers.isEmpty()) {
      return Map.of();
    }

    Set<String> validTickers = tickers.stream()
        .filter(ticker -> ticker != null && !ticker.isBlank())
        .collect(Collectors.toSet());

    if (validTickers.isEmpty()) {
      return Map.of();
    }

    return companyNameRepository.findByTickerIn(validTickers).stream()
        .collect(Collectors.toMap(CompanyName::getTicker, CompanyName::getCompanyName, (existing, replacement) -> existing));
  }

  private String toDisplayTicker(String ticker, Map<String, String> tickerNameMap) {
    if (ticker == null || ticker.isBlank()) {
      return ticker;
    }
    return tickerNameMap.getOrDefault(ticker, ticker);
  }
}

