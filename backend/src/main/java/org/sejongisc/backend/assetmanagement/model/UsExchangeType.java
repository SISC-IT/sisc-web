package org.sejongisc.backend.assetmanagement.model;

import java.util.Arrays;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;

/**
 * 키움 미국주식 API의 거래소구분(stex_tp) 코드.
 *
 * <p>usa06012(미국주식 일 차트) 요청의 stex_tp 필드에 사용된다.
 */
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
