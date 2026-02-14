package org.sejongisc.backend.common.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@Builder
public class ErrorResponse {

  private ErrorCode errorCode;
  private String message;
  private HttpStatus status;

  public static ErrorResponse of(ErrorCode errorCode) {
    return ErrorResponse.builder()
            .errorCode(errorCode)
            .message(errorCode.getMessage())
            .status(errorCode.getStatus())
            .build();
  }
}
