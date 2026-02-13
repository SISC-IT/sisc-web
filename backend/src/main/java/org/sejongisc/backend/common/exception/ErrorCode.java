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

  INVALID_BACKTEST_PARAMS(HttpStatus.BAD_REQUEST, "유효하지 않은 백테스트 파라미터입니다."),

  BACKTEST_INDICATOR_NOT_FOUND(HttpStatus.BAD_REQUEST, "지원하지 않는 보조지표입니다."),

  BACKTEST_OPERAND_INVALID(HttpStatus.BAD_REQUEST, "전략 피연산자(Operand) 설정이 올바르지 않습니다."),

  BACKTEST_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "백테스트 실행 중 오류가 발생했습니다."),

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

  ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 포인트 계정을 찾을 수 없습니다."),

  ACCOUNT_REQUIRED(HttpStatus.BAD_REQUEST, "계정 정보는 필수입니다."),

  POINT_TRANSACTION_TOTAL_MISMATCH(HttpStatus.BAD_REQUEST, "포인트 거래 내역의 합계가 0이 아닙니다."),

  // AUTH

  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),

  MISSING_AUTH_TOKEN(HttpStatus.UNAUTHORIZED, "인증 토큰이 필요합니다."),

  MISSING_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 필요합니다."),

  INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 엑세스 토큰입니다."),

  // EMAIL
  EMAIL_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "이메일 인증 코드를 찾을 수 없습니다"),

  EMAIL_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),

  EMAIL_INVALID_EMAIL(HttpStatus.BAD_REQUEST, "유효하지 않은 이메일입니다."),

  EMAIL_ALREADY_VERIFIED(HttpStatus.BAD_REQUEST, "24시간 이내에 이미 인증된 이메일입니다."),

  // QUANTBOT
  EXECUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 퀀트봇 실행 내역이 존재하지 않습니다."),

  XAI_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 XAI 리포트가 존재하지 않습니다."),

  // USER

  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
  DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 사용 중인 전화번호입니다."),
  DUPLICATE_USER(HttpStatus.CONFLICT, "이미 가입된 사용자입니다."),
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),

  // EXCEL

  INVALID_FILE_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다. .xlsx 파일을 업로드해주세요."),
  INVALID_EXCEL_STRUCTURE(HttpStatus.UNPROCESSABLE_ENTITY, "엑셀 양식이 일치하지 않습니다. 필수 컬럼을 확인해주세요."),
  EMPTY_FILE(HttpStatus.BAD_REQUEST, "업로드된 파일이 비어있습니다."),

  // BETTING

  STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "주식 종목이 존재하지 않습니다."),
  BET_ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 라운드입니다."),
  BET_DUPLICATE(HttpStatus.CONFLICT, "이미 이 라운드에 베팅했습니다."),
  BET_ROUND_CLOSED(HttpStatus.CONFLICT, "베팅 가능 시간이 아닙니다."),
  BET_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 베팅을 찾을 수 없습니다."),
  BET_POINT_TOO_LOW(HttpStatus.CONFLICT, "베팅 포인트는 10 이상이어야 합니다."),
  BET_ROUND_NOT_CLOSED(HttpStatus.CONFLICT, "닫히지 않은 배팅입니다."),
  BET_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 취소되었거나 처리된 베팅입니다."),

  // BOARD

  POST_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 게시물을 찾을 수 없습니다."),

  INVALID_BOARD_OWNER(HttpStatus.FORBIDDEN, "게시판 수정/삭제 권한이 없습니다."),

  INVALID_POST_OWNER(HttpStatus.FORBIDDEN, "게시물 수정/삭제 권한이 없습니다."),

  COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 댓글을 찾을 수 없습니다."),

  INVALID_COMMENT_OWNER(HttpStatus.FORBIDDEN, "댓글 수정/삭제 권한이 없습니다."),

  INVALID_PARENT_COMMENT(HttpStatus.BAD_REQUEST, "부모 댓글이 해당 게시판에 속해 있지 않습니다."),

  ALREADY_CHILD_COMMENT(HttpStatus.BAD_REQUEST, "대댓글에는 다시 대댓글을 작성할 수 없습니다."),

  BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 게시판을 찾을 수 없습니다."),

  INVALID_BOARD_TYPE(HttpStatus.BAD_REQUEST, "상위 게시판에는 글을 작성할 수 없습니다."),

  // ATTENDANCE

  SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 출석 세션이 존재하지 않습니다."),

  ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 출석 라운드가 존재하지 않습니다."),

  ROUND_NOT_ACTIVE(HttpStatus.FORBIDDEN, "출석 라운드가 진행 중이 아닙니다."),

  ROUND_DATE_REQUIRED(HttpStatus.BAD_REQUEST, "출석 라운드 날짜가 필요합니다."),

  START_AT_REQUIRED(HttpStatus.BAD_REQUEST, "출석 라운드 시작 시간이 필요합니다."),

  ROUND_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "출석 라운드 이름이 필요합니다."),

  STATUS_REQUIRED(HttpStatus.BAD_REQUEST, "출석 상태가 필요합니다."),

  DEVICE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "출석 체크를 위한 기기 ID가 필요합니다."),

  END_AT_MUST_BE_AFTER_START_AT(HttpStatus.BAD_REQUEST, "출석 라운드 종료 시간은 시작 시간 이후여야 합니다."),

  QR_TOKEN_MALFORMED(HttpStatus.BAD_REQUEST, "QR 토큰 형식이 올바르지 않습니다."),

  ALREADY_CHECKED_IN(HttpStatus.FORBIDDEN, "이미 출석 체크되었습니다."),

  INVALID_ATTENDANCE_STATUS(HttpStatus.BAD_REQUEST, "유효하지 않은 출석 상태입니다."),

  DEVICE_ALREADY_USED(HttpStatus.FORBIDDEN, "해당 기기는 이미 출석 체크에 사용되었습니다."),

  ALREADY_JOINED(HttpStatus.FORBIDDEN, "이미 출석 세션에 참여 중입니다."),

  NOT_SESSION_MEMBER(HttpStatus.FORBIDDEN, "출석 세션의 멤버가 아닙니다."),

  TARGET_NOT_SESSION_MEMBER(HttpStatus.BAD_REQUEST, "대상 사용자가 출석 세션의 멤버가 아닙니다."),

  CANNOT_DEMOTE_OWNER(HttpStatus.BAD_REQUEST, "출석 세션 소유자는 강등할 수 없습니다.");
  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }
}