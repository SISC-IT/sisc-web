package org.sejongisc.backend.point.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointHistory extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long pointHistoryId;

  @Column(nullable = false)
  private int amount;               // 포인트 증감량 (+/-)

  @Enumerated(EnumType.STRING)
  private PointReason reason;       // 포인트 변경 이유 (enum)

  @Enumerated(EnumType.STRING)
  private PointOrigin pointOrigin;  // 연관된 테이블 이름

  @Column(name = "point_origin_id", columnDefinition = "uuid")
  private UUID pointOriginId;       // 연관된 테이블의 ID

  @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
  private UUID userId;

  public static PointHistory of(UUID userId, int amount, PointReason reason, PointOrigin origin, UUID originId) {
    return PointHistory.builder()
        .userId(userId)
        .amount(amount)
        .reason(reason)
        .pointOrigin(origin)
        .pointOriginId(originId)
        .build();
  }
}
