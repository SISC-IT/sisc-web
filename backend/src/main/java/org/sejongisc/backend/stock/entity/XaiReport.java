package org.sejongisc.backend.stock.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Table(name = "xai_reports")
public class XaiReport {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String ticker;
  private String signal;
  private BigDecimal price;
  private LocalDate date;

  @Column(columnDefinition = "text")
  private String report;
  private LocalDateTime createdAt;

}

