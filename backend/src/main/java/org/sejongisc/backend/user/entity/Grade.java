package org.sejongisc.backend.user.entity;

public enum Grade {
  NEW_MEMBER, // 신입부원
  ASSOCIATE_MEMBER, // 준회원
  REGULAR_MEMBER, // 정회원
  SENIOR; // 선배/OB

  public static Grade fromString(String gradeStr) {
    if (gradeStr == null) return NEW_MEMBER;

    if (gradeStr.contains("정회원")) return REGULAR_MEMBER;
    if (gradeStr.contains("준회원")) return ASSOCIATE_MEMBER;
    if (gradeStr.contains("선배") || gradeStr.contains("OB")) return SENIOR;

    return NEW_MEMBER;
  }
}
