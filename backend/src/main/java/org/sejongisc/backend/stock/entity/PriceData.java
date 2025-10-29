package org.sejongisc.backend.stock.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "price_data")
@IdClass(PriceDataId.class)
public class PriceData {

  @Id
  private String ticker;

  @Id
  private LocalDate date;

  private BigDecimal open;
  private BigDecimal high;
  private BigDecimal low;
  @Column(name = "close")
  private BigDecimal closePrice; // 'close'는 예약어일 수 있어 필드명 변경
  private Long volume;
  private BigDecimal adjustedClose;
}
