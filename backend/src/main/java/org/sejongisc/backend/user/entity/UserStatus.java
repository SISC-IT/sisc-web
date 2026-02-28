package org.sejongisc.backend.user.entity;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserStatus {
  ACTIVE("활동 중"),
  INACTIVE("활동 중지"),
  GRADUATED("졸업생"),
  OUT("탈퇴");

  private final String description; // 한글 명칭
}
