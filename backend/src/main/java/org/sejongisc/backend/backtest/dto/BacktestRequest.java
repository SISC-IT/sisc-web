package org.sejongisc.backend.backtest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.sejongisc.backend.user.entity.User;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BacktestRequest {
  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private User user;

  @Schema(description = "템플릿 ID")
  private UUID templateId;

  @Schema(description = "백테스트 ID")
  private Long backtestRunId;

  @Schema(description = "백테스트 제목", defaultValue = "골든크로스 + RSI (AAPL)")
  private String title;

  @Schema(description = "백테스트 시작일")
  private LocalDate startDate;

  @Schema(description = "백테스트 종료일")
  private LocalDate endDate;

  @Schema(description = "백테스트 실행 요청 JSON")
  private BacktestRunRequest strategy;

  // 백테스트 리스트 삭제
  @Schema(description = "삭제할 백테스트 실행 리스트")
  private List<Long> backtestRunIds;
}
