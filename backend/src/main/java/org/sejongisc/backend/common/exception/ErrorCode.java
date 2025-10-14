package org.sejongisc.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  // GLOBAL

  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 발생했습니다."),

  // BACKTEST

  BACKTEST_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 백테스트가 존재하지 않습니다."),

  BACKTEST_OWNER_MISMATCH(HttpStatus.FORBIDDEN, "백테스트 소유자가 아닙니다."),

  BACKTEST_TEMPLATE_MISMATCH(HttpStatus.BAD_REQUEST, "백테스트 템플릿이 일치하지 않습니다."),

  BACKTEST_METRICS_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 백테스트 결과 정보가 존재하지 않습니다."),

  // TEMPLATE

  TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 템플릿이 존재하지 않습니다."),

  TEMPLATE_OWNER_MISMATCH(HttpStatus.FORBIDDEN, "템플릿 소유자가 아닙니다."),

  // POINT

  INVALID_PERIOD(HttpStatus.BAD_REQUEST, "유효하지 않은 리더보드 기간입니다."),

  INVALID_POINT_AMOUNT(HttpStatus.BAD_REQUEST, "포인트 변동량은 0일 수 없습니다"),

  NOT_ENOUGH_POINT_BALANCE(HttpStatus.BAD_REQUEST, "포인트 잔액이 부족합니다"),

  CONCURRENT_UPDATE(HttpStatus.CONFLICT, "동시성 업데이트에 실패했습니다. 다시 시도해주세요."),

  // AUTH

  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),

  MISSING_AUTH_TOKEN(HttpStatus.UNAUTHORIZED, "인증 토큰이 필요합니다."),

  INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 엑세스 토큰입니다."),

  // USER

  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
  DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 사용 중인 전화번호입니다."),
  DUPLICATE_USER(HttpStatus.CONFLICT, "이미 가입된 사용자입니다."),
  
  // BETTING

  STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "주식 종목이 존재하지 않습니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }
}