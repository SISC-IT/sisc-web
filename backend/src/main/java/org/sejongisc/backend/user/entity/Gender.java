package org.sejongisc.backend.user.entity;

public enum Gender {
  MALE,
  FEMALE;

  public static Gender fromString(String genderStr) {
    if (genderStr == null || genderStr.isBlank()) {
      return null;
    }

    if (genderStr.contains("남")) return MALE;
    if (genderStr.contains("여")) return FEMALE;

    return null;
  }
}
