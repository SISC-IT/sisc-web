package org.sejongisc.backend.point.entity;

public enum TransactionReason {
  ATTENDANCE, // 출석 체크 보상
  SIGNUP_REWARD, // 회원가입
  BETTING_STAKE, // 베팅 참여
  BETTING_REWARD, // 베팅 보상
  BETTING_REFUND, // 베팅 환불
  BETTING_CANCEL, // 베팅 취소
  BETTING_RESIDUAL, // 베팅 잔여금 이동
  SYSTEM_ADJUSTMENT, // 관리자 수동 조정
  MIGRATION // 마이그레이션
}
