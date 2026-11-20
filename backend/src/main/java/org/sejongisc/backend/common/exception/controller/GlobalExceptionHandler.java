package org.sejongisc.backend.common.exception.controller;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.common.exception.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  /**
   * 1) 커스텀 예외 처리
   */
  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
    log.error("CustomException 발생: {}", e.getMessage());

    return errorResponse(e.getErrorCode());
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
    // 타입 변환 실패(isBindingFailure)의 기본 메시지에는 입력값과 내부 타입 이름이 들어 있어 공통 메시지를 쓴다.
    String message = fieldError != null && !fieldError.isBindingFailure()
        ? fieldError.getDefaultMessage()
        : errorCode.getMessage();
    return ResponseEntity
        .status(errorCode.getStatus())
        .body(ErrorResponse.builder().errorCode(errorCode).message(message).build());
  }

  /**
   * 3) 요청 형식 오류 처리: 필수 헤더·파라미터 누락, 타입 불일치, 읽을 수 없는 본문(깨진 JSON, 없는 enum 값)
   * - 예외 메시지에 입력값이 들어 있어 로그에는 예외 종류만 남기고, 응답은 공통 메시지로 보낸다.
   */
  @ExceptionHandler({
      MissingRequestHeaderException.class,
      MissingServletRequestParameterException.class,
      MethodArgumentTypeMismatchException.class,
      HttpMessageNotReadableException.class
  })
  public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
    log.warn("잘못된 요청 형식: {}", e.getClass().getSimpleName());
    return errorResponse(ErrorCode.INVALID_INPUT_VALUE);
  }

  /**
   * 4) 권한 없음 처리: @PreAuthorize 거부 등
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
    log.warn("권한 없음: {}", e.getClass().getSimpleName());
    return errorResponse(ErrorCode.ACCESS_DENIED);
  }

  /**
   * 5) 없는 주소 처리
   */
  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
    return errorResponse(ErrorCode.RESOURCE_NOT_FOUND);
  }

  /**
   * 6) 지원하지 않는 HTTP 메서드 처리
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
    return errorResponse(ErrorCode.METHOD_NOT_ALLOWED);
  }

  /**
   * 7) 그 외 예외 처리
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception e) {
    log.error("Unhandled Exception 발생: {}", e.getMessage(), e);

    // 예상치 못한 예외 => 500
    // 예외 메시지에는 SQL·입력값 등 내부 정보가 들어 있을 수 있어 로그에만 남기고 응답은 공통 메시지로 보낸다.
    return errorResponse(ErrorCode.INTERNAL_SERVER_ERROR);
  }

  private static ResponseEntity<ErrorResponse> errorResponse(ErrorCode errorCode) {
    return ResponseEntity
        .status(errorCode.getStatus())
        .body(ErrorResponse.of(errorCode));
  }
}