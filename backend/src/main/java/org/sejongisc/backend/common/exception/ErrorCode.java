package org.sejongisc.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  // GLOBAL

  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 발생했습니다."),

  // PRICE DATA

  PRICE_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 주식의 가격 데이터가 존재하지 않습니다."),

  // BACKTEST

  INVALID_BACKTEST_JSON_PARAMS(HttpStatus.BAD_REQUEST, "유효하지 않은 paramsJson 요청값 입니다."),

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

  // EMAIL
  EMAIL_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "이메일 인증 코드를 찾을 수 없습니다"),
  EMAIL_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),
  EMAIL_INVALID_EMAIL(HttpStatus.BAD_REQUEST, "유효하지 않은 이메일입니다"),
  EMAIL_ALREADY_VERIFIED(HttpStatus.BAD_REQUEST, "24시간 이내에 이미 인증된 이메일입니다."),

  // USER

  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
  DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 사용 중인 전화번호입니다."),
  DUPLICATE_USER(HttpStatus.CONFLICT, "이미 가입된 사용자입니다."),
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
  
  // BETTING

  STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "주식 종목이 존재하지 않습니다."),
  BET_ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 라운드입니다."),
  BET_DUPLICATE(HttpStatus.CONFLICT, "이미 이 라운드에 베팅했습니다."),
  BET_ROUND_CLOSED(HttpStatus.CONFLICT, "베팅 가능 시간이 아닙니다."),
  BET_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 베팅을 찾을 수 없습니다."),
  BET_POINT_TOO_LOW(HttpStatus.CONFLICT, "베팅 포인트는 10 이상이어야 합니다."),
  BET_ROUND_NOT_CLOSED(HttpStatus.CONFLICT, "닫히지 않은 배팅입니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }
}