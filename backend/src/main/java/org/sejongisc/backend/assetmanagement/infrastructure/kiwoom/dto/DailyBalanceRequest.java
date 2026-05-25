package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DailyBalanceRequest {
  @JsonProperty("qry_dt")
  private String queryDate;
}
