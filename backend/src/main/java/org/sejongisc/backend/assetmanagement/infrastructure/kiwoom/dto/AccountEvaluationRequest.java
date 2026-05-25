package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AccountEvaluationRequest {
  @JsonProperty("qry_tp")
  private String queryType;

  @JsonProperty("dmst_stex_tp")
  private String domesticExchangeType;

  public static AccountEvaluationRequest defaultRequest(String domesticExchangeType) {
    return AccountEvaluationRequest.builder()
        .queryType("0")
        .domesticExchangeType(domesticExchangeType)
        .build();
  }
}
