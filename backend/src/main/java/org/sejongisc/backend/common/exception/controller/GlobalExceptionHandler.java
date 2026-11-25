package org.sejongisc.backend.common.exception.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.common.exception.ErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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
   * 3) 요청 형식 오류 처리: 필수 헤더·파라미터·파일 누락, 타입 불일치, 읽을 수 없는 본문(깨진 JSON, 없는 enum 값)
   * - 예외 메시지에 입력값이 들어 있어 로그에는 예외 종류만 남기고, 응답은 공통 메시지로 보낸다.
   */
  @ExceptionHandler({
      MissingRequestHeaderException.class,
      MissingServletRequestParameterException.class,
      MissingServletRequestPartException.class,
      MethodArgumentTypeMismatchException.class,
      HttpMessageNotReadableException.class
  })
  public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
    log.warn("잘못된 요청 형식: {}", e.getClass().getSimpleName());
    return errorResponse(ErrorCode.INVALID_INPUT_VALUE);
  }

  /**
   * 4) @Validated·엔티티 검증 실패 처리
   * - 서비스 메서드(@Validated) 검증 실패는 입력 오류라 400이다.
   * - 엔티티 검증 실패는 서버가 잘못된 값을 저장하려 한 버그라 500이다.
   * - 메시지에 입력값이 들어갈 수 있어 로그에는 필드 경로만 남긴다.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
    Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
    List<String> paths = violations.stream().map(GlobalExceptionHandler::fieldPath).toList();
    if (violations.stream().allMatch(GlobalExceptionHandler::isMethodValidation)) {
      log.warn("검증 실패: paths={}", paths);
      return errorResponse(ErrorCode.INVALID_INPUT_VALUE);
    }
    log.error("엔티티 검증 실패: paths={}", paths);
    return errorResponse(ErrorCode.INTERNAL_SERVER_ERROR);
  }

  // 메서드 검증의 경로는 메서드 노드로 시작한다(sendCode.email). 엔티티 검증은 필드 노드로 시작한다.
  private static boolean isMethodValidation(ConstraintViolation<?> violation) {
    Iterator<Path.Node> nodes = violation.getPropertyPath().iterator();
    return nodes.hasNext() && nodes.next().getKind() == ElementKind.METHOD;
  }

  // 클래스 이름 + 노드 이름. Path.toString()은 Map 키를 담으므로(aliases[키]) 쓰지 않는다.
  private static String fieldPath(ConstraintViolation<?> violation) {
    String path = StreamSupport.stream(violation.getPropertyPath().spliterator(), false)
        .map(Path.Node::getName)
        .filter(Objects::nonNull)
        .collect(Collectors.joining("."));
    return violation.getRootBeanClass().getSimpleName() + "." + path;
  }

  /**
   * 5) multipart 처리 실패
   * - 원인 예외가 없으면 multipart가 아닌 요청(400), 있으면 업로드 처리 중 실패(서버 문제 포함, 500)
   */
  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<ErrorResponse> handleMultipart(MultipartException e) {
    if (e.getCause() != null) {
      return handleException(e);
    }
    log.warn("잘못된 요청 형식: {}", e.getClass().getSimpleName());
    return errorResponse(ErrorCode.INVALID_INPUT_VALUE);
  }

  /**
   * 6) 업로드 크기 제한 초과 처리
   * - MultipartException의 하위 예외지만 Spring 기본 처리처럼 413으로 보낸다.
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
    log.warn("업로드 크기 제한 초과: maxUploadSize={}", e.getMaxUploadSize());
    return errorResponse(ErrorCode.UPLOAD_SIZE_EXCEEDED);
  }

  /**
   * 7) 권한 없음 처리: @PreAuthorize 거부 등
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
    log.warn("권한 없음: {}", e.getClass().getSimpleName());
    return errorResponse(ErrorCode.ACCESS_DENIED);
  }

  /**
   * 8) 없는 주소 처리
   */
  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
    return errorResponse(ErrorCode.RESOURCE_NOT_FOUND);
  }

  /**
   * 9) 지원하지 않는 HTTP 메서드 처리
   * - e.getHeaders()에 Spring 기본 처리와 같은 Allow 헤더(지원하는 메서드)가 들어 있다.
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
    return errorResponse(ErrorCode.METHOD_NOT_ALLOWED, e.getHeaders());
  }

  /**
   * 10) 지원하지 않는 Content-Type 처리
   * - e.getHeaders()에 Spring 기본 처리와 같은 Accept 헤더(PATCH면 Accept-Patch도)가 들어 있다.
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
    return errorResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE, e.getHeaders());
  }

  /**
   * 11) 그 외 예외 처리
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception e) {
    log.error("Unhandled Exception 발생: {}", e.getMessage(), e);

    // 예상치 못한 예외 => 500
    // 예외 메시지에는 SQL·입력값 등 내부 정보가 들어 있을 수 있어 로그에만 남기고 응답은 공통 메시지로 보낸다.
    return errorResponse(ErrorCode.INTERNAL_SERVER_ERROR);
  }

  private static ResponseEntity<ErrorResponse> errorResponse(ErrorCode errorCode) {
    return errorResponse(errorCode, HttpHeaders.EMPTY);
  }

  private static ResponseEntity<ErrorResponse> errorResponse(ErrorCode errorCode, HttpHeaders headers) {
    return ResponseEntity
        .status(errorCode.getStatus())
        .headers(headers)
        .body(ErrorResponse.of(errorCode));
  }
}