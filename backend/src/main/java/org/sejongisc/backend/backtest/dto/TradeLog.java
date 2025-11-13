package org.sejongisc.backend.backtest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TradeLog {
  public enum Type { BUY, SELL }
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