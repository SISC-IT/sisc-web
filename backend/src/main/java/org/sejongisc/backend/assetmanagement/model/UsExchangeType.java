package org.sejongisc.backend.assetmanagement.model;

import java.util.Arrays;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

public enum UsExchangeType {
  AMEX("NA"),
  NASDAQ("ND"),
  NYSE("NY");

  private final String code;

  UsExchangeType(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static UsExchangeType from(String value) {
    if (value == null || value.isBlank()) {
      throw new CustomException(ErrorCode.INVALID_KIWOOM_US_EXCHANGE_TYPE);
    }

    String trimmed = value.trim();

    return Arrays.stream(values())
        .filter(type -> type.name().equalsIgnoreCase(trimmed) || type.code.equalsIgnoreCase(trimmed))
        .findFirst()
        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_KIWOOM_US_EXCHANGE_TYPE));
  }
}
