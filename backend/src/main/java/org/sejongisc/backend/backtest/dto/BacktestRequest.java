package org.sejongisc.backend.backtest.dto;

import lombok.Getter;
import lombok.Setter;
import org.sejongisc.backend.user.entity.User;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BacktestRequest {
  // hidden 설정하기
  private UUID userId;

  private UUID templateId;
  private Long backtestRunId;

  private String title;
  private String paramsJson;
  private LocalDate startDate;
  private LocalDate endDate;
  // 백테스트 리스트 삭제
  private List<Long> backtestRunIds;
}
