package org.sejongisc.backend.assetmanagement.model;

import java.util.Arrays;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

public enum DomesticExchangeType {
  KRX,
  NXT;

  public static DomesticExchangeType from(String value) {
    if (value == null || value.isBlank()) {
      return KRX;
    }

    return Arrays.stream(values())
        .filter(type -> type.name().equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_KIWOOM_EXCHANGE_TYPE));
  }
}
