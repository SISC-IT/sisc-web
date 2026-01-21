package org.sejongisc.backend.attendance.entity;

import lombok.RequiredArgsConstructor;

/**
 * 세션 역할
 */
@RequiredArgsConstructor
public enum SessionRole {
  OWNER("세션 소유자"),      // 세션 생성자
  MANAGER("관리자"),    // 세션 관리자
  PARTICIPANT("참가자"); // 세션 참가자
  private final String description;
}
