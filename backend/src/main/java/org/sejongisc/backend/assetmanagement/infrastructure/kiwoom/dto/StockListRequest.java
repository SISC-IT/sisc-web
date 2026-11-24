package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StockListRequest {
  @JsonProperty("mrkt_tp")
  private String marketType;

  public static StockListRequest of(String marketType) {
    return new StockListRequest(marketType);
  }
}
