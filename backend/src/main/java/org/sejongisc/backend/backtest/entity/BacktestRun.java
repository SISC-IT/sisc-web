package org.sejongisc.backend.backtest.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.template.entity.Template;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestRun {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "template_id", nullable = false)
  private Template template;

  //@ManyToOne(fetch = FetchType.LAZY)
  //@JoinColumn(name = "user_id", nullable = false)
  //private User user;

  // 조건/종목 등 파라미터(JSONB). 가장 단순하게 String으로 보관
  @Column(name = "params", columnDefinition = "jsonb")
  private String paramsJson;

  // 기간: ERD는 daterange지만, JPA 친화적으로 start/end 두 컬럼로 매핑
  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  // 실행 시간들
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
}
