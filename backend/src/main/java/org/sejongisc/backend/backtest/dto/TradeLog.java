package org.sejongisc.backend.backtest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TradeLog {
  public enum Type {
    BUY,
    SELL,
    SELL_FORCED // 기본 청산 기간에 의한 강제 매도 구분용 추가
  }
  public final Type type;
  public final LocalDateTime time;
  public final BigDecimal price;
  public final BigDecimal shares;

  public TradeLog(Type type, LocalDateTime time, BigDecimal price, BigDecimal shares) {
    this.type = type;
    this.time = time;
    this.price = price;
    this.shares = shares;
  }
}