package org.sejongisc.backend.stock.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.stock.dto.HoldingDto;
import org.sejongisc.backend.stock.dto.TradeLogDto;
import org.sejongisc.backend.stock.entity.Execution;
import org.sejongisc.backend.stock.entity.XaiReport;
import org.sejongisc.backend.stock.repository.ExecutionRepository;
import org.sejongisc.backend.stock.repository.XaiReportRepository;
import org.springframework.stereotype.Service;

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
  public XaiReport getXaiReportByExecutionId(Long executionId) {
    Execution exec = executionRepository.findById(executionId)
        .orElseThrow(() -> new CustomException(ErrorCode.EXECUTION_NOT_FOUND));

    XaiReport xaiReport = exec.getXaiReport();
    if(xaiReport == null) {
      throw new CustomException(ErrorCode.XAI_REPORT_NOT_FOUND);
    }

    return xaiReport;
  }
}

