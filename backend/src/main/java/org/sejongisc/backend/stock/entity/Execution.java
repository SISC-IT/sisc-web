package org.sejongisc.backend.stock.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Table(name = "executions")
public class Execution {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY) // SERIAL
  private Long id;
  private String ticker;
  private LocalDate signalDate;
  private BigDecimal signalPrice;
  private String signal;
  private LocalDate fillDate;
  private BigDecimal fillPrice;
  private Integer qty;
  private String side;
  private BigDecimal value;
  private BigDecimal commission;
  private BigDecimal cashAfter;
  private Integer positionQty;
  private BigDecimal avgPrice;
  private BigDecimal pnlRealized;
  private BigDecimal pnlUnrealized;
  private OffsetDateTime createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "xai_report_id")
  private XaiReport xaiReport;

}
