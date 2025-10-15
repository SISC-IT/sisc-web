package org.sejongisc.backend.common.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ErrorResponse {

  private ErrorCode errorCode;
  private String errorMessage;

  public static ErrorResponse of(ErrorCode errorCode) {
    return ErrorResponse.builder()
            .errorCode(errorCode)
            .errorMessage(errorCode.getMessage())
            .build();
  }
}
