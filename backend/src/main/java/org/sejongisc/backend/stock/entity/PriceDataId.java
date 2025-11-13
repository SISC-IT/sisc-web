package org.sejongisc.backend.stock.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;


public class PriceDataId implements Serializable {
  private String ticker;
  private LocalDate date;

  /**
   * JPA 는 프록시 객체 생성 등을 위해 기본 생성자가 필요함
   */
  public PriceDataId() {
  }

  public PriceDataId(String ticker, LocalDate date) {
    this.ticker = ticker;
    this.date = date;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PriceDataId that = (PriceDataId) o;
    return Objects.equals(ticker, that.ticker) &&
        Objects.equals(date, that.date);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ticker, date);
  }
}