package org.sejongisc.backend.backtest.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.user.entity.User;

import java.util.UUID;


@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Template extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "template_id", columnDefinition = "uuid")
  private UUID templateId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  @JsonIgnore   // TODO : 추후 isPublic 필드에 따른 직렬화 제어 필요
  private User user;

  private String title;           // 템플릿 제목
  private String description;     // 템플릿 설명
  private Boolean isPublic;       // 템플릿 공개 여부
  private int bookmarkCount;      // 북마크 횟수
  private int likeCount;          // 좋아요 개수
  // DB 트랜잭션에서 동시성 업데이트(likeCount + 1) 충돌 관리가 필요
  // JPA 기본 @Version 낙관적 락을 붙이거나, DB update ... set like_count = like_count + 1 쿼리로 처리

  public void update(String title, String description, Boolean isPublic) {
    this.title = title;
    this.description = description;
    this.isPublic = isPublic;
  }

  public void incrementBookmarkCount() {
    this.bookmarkCount += 1;
  }

  public void decrementBookmarkCount() {
    if (this.bookmarkCount > 0) {
      this.bookmarkCount -= 1;
    }
    //TODO : 로깅처리
  }

  public void incrementLikeCount() {
    this.likeCount += 1;
  }

  public void decrementLikeCount() {
    if (this.likeCount > 0) {
      this.likeCount -= 1;
    }
    //TODO : 로깅처리
  }

  public static Template of(User user, String title, String description, Boolean isPublic) {
    return Template.builder()
        .user(user)
        .title(title)
        .description(description)
        .isPublic(isPublic)
        .bookmarkCount(0)
        .likeCount(0)
        .build();
  }
}
