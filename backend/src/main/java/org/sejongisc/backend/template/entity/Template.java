package org.sejongisc.backend.template.entity;


import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.util.UUID;


@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Template extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  //@ManyToOne
  //private User user;

  private String title;           // 템플릿 제목
  private String description;     // 템플릿 설명
  private Boolean isPublic;       // 템플릿 공개 여부
  private int bookmarkCount;      // 북마크 횟수
  private int likeCount;          // 좋아요 개수
  // DB 트랜잭션에서 동시성 업데이트(likeCount + 1) 충돌 관리가 필요
  // JPA 기본 @Version 낙관적 락을 붙이거나, DB update ... set like_count = like_count + 1 쿼리로 처리
}
