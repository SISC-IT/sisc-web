package org.sejongisc.backend.common.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ErrorResponse {

  private ErrorCode errorCode;
  private String message;

  public static ErrorResponse of(ErrorCode errorCode) {
    return ErrorResponse.builder()
            .errorCode(errorCode)
            .message(errorCode.getMessage())
            .build();
  }
}
