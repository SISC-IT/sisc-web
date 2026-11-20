package org.sejongisc.backend.common.exception.controller;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.common.exception.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  /**
   * 1) 커스텀 예외 처리
   */
  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
    log.error("CustomException 발생: {}", e.getMessage());

    ErrorCode errorCode = e.getErrorCode();
    return ResponseEntity
        .status(errorCode.getStatus())
        .body(ErrorResponse.of(errorCode));
  }

  /**
   * 2) @Valid 검증 실패 처리
   * - @RequestBody의 MethodArgumentNotValidException은 BindException을 상속하므로 함께 처리된다.
   */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ErrorResponse> handleBindException(BindException e) {
    // e.getMessage()에는 거부된 입력값(비밀번호 등)이 들어 있으므로 필드 이름만 남긴다.
    List<String> fields = e.getFieldErrors().stream().map(FieldError::getField).toList();
    log.warn("요청 값 검증 실패: fields={}", fields);

    ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
    FieldError fieldError = e.getFieldError();
    String message = fieldError != null ? fieldError.getDefaultMessage() : errorCode.getMessage();
    return ResponseEntity
        .status(errorCode.getStatus())
        .body(ErrorResponse.builder().errorCode(errorCode).message(message).build());
  }

  /**
   * 3) 그 외 예외 처리
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleException(Exception e) {
    log.error("Unhandled Exception 발생: {}", e.getMessage(), e);

    // 예상치 못한 예외 => 500
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
  }
}