package org.sejongisc.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  // GLOBAL
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 발생했습니다."),

  // AUTH

  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),

  MISSING_AUTH_TOKEN(HttpStatus.UNAUTHORIZED, "인증 토큰이 필요합니다."),

  INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 엑세스 토큰입니다."),

  // USER

  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),

  // ATTENDANCE
  ATTENDANCE_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 출석 세션을 찾을 수 없습니다."),
  INVALID_ATTENDANCE_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 출석 코드입니다."),
  DUPLICATE_ATTENDANCE(HttpStatus.CONFLICT, "이미 출석하였습니다."),
  ATTENDANCE_TIME_EXPIRED(HttpStatus.BAD_REQUEST, "출석 시간이 지났습니다."),
  ATTENDANCE_NOT_STARTED(HttpStatus.BAD_REQUEST, "아직 출석 시간이 되지 않았습니다."),

  // LOCATION 관련 에러
  LOCATION_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "출석 위치를 벗어났습니다."),
  INVALID_LOCATION_DATA(HttpStatus.BAD_REQUEST, "위치 정보가 올바르지 않습니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }
}