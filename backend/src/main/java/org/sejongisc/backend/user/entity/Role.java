package org.sejongisc.backend.user.entity;

// 일반 회원가입 : 회장 승인이 있어야만 설정 가능
// 엑셀 회원가입 : 회장 승인 없이 설정 가능 (user.isManagerPosition 으로 판단)
public enum Role {
  SYSTEM_ADMIN,      // 시스템 관리자
  PRESIDENT,          // 회장
  VICE_PRESIDENT,     // 부회장
  TEAM_LEADER,        // 팀장
  TEAM_MEMBER,        // 부원
  PENDING_MEMBER;     // 대기회원 (회장이 승인 전 상태)
  // 추가 가능 : SENIOR (선배/OB): 게시물 열람 위주 (포인트 활동 등은 제한 가능)

  public static Role fromPosition(String position) {
    if (position == null || position.isBlank()) {
      return TEAM_MEMBER;
    }

    if (position.contains("회장") && !position.contains("부회장")) {
      return PRESIDENT;
    }

    if (position.contains("부회장") || position.contains("부대표자")) {
      return VICE_PRESIDENT;
    }

    if (position.contains("팀장")) {
      return TEAM_LEADER;
    }

    return TEAM_MEMBER;
  }
}