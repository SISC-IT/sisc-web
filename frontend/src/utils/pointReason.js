const POINT_REASON_LABELS = {
  ATTENDANCE: '출석',
  SIGNUP_REWARD: '회원가입',
  BETTING_STAKE: '베팅 참여',
  BETTING_REWARD: '베팅 적중',
  BETTING_REFUND: '베팅 환불',
  BETTING_CANCEL: '베팅 취소',
  BETTING_RESIDUAL: '베팅 잔액 정산',
  SYSTEM_ADJUSTMENT: '관리자 조정',
  MIGRATION: '데이터 이전',
};

export const formatPointReason = (reason) => {
  if (!reason) {
    return '기타';
  }

  return POINT_REASON_LABELS[reason] ?? reason;
};
