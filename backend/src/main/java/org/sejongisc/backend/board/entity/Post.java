package org.sejongisc.backend.board.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.user.entity.User;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Post extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID postId;

  // 작성자
  @ManyToOne(fetch = FetchType.LAZY)
  private User user;

  // 게시판 타입
  @Enumerated(EnumType.STRING)
  private BoardType boardType;

  // 제목
  @Column(nullable = false)
  private String title;

  // 내용
  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  // 게시글 타입
  @Enumerated(EnumType.STRING)
  private PostType postType;

  // 북마크 수
  @Builder.Default
  private Integer bookmarkCount = 0;

  // 좋아요 수
  @Builder.Default
  private Integer likeCount = 0;

  // 댓글 수
  @Builder.Default
  private Integer commentCount = 0;
}
