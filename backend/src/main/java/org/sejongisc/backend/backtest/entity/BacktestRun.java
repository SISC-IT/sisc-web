package org.sejongisc.backend.backtest.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.sejongisc.backend.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestRun {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "template_id", nullable = true)
  private Template template;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  private String title;

  @Enumerated(EnumType.STRING)
  private BacktestStatus status;

  // 조건/종목 등 파라미터(JSONB). 가장 단순하게 String으로 보관
  // 기록 (불변성 목적) : 생성된 순간의 상태 박제 목적
  @JdbcTypeCode(SqlTypes.JSON)
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

  // 오류 발생 시 기록
  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  public void updateTemplate(Template template) {
    this.template = template;
  }
}
