package org.sejongisc.backend.board.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
  @JoinColumn(name = "user_id")
  private User user;

  // 게시판 타입
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "board_id")
  private Board board;

  // 제목
  @Column(nullable = false)
  private String title;

  // 내용
  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  // 북마크 수
  @Builder.Default
  private Integer bookmarkCount = 0;

  // 좋아요 수
  @Builder.Default
  private Integer likeCount = 0;

  // 댓글 수
  @Builder.Default
  private Integer commentCount = 0;

  @Version
  private Long version;
}
