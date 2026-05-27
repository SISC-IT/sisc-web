package org.sejongisc.backend.publicweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.sejongisc.backend.board.entity.PostContentFormat;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.user.entity.User;

@Entity
@Table(name = "public_page")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicPage extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "public_page_id", nullable = false, updatable = false)
  private UUID publicPageId;

  @Enumerated(EnumType.STRING)
  @Column(name = "page_type", nullable = false, unique = true, length = 30)
  private PublicPageType pageType;

  @Column(nullable = false)
  private String title;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PostContentFormat contentFormat = PostContentFormat.PLAIN_TEXT;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(columnDefinition = "TEXT")
  private String contentJson;

  @Column(columnDefinition = "TEXT")
  private String contentHtml;

  @Column(columnDefinition = "TEXT")
  private String contentText;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "updated_by")
  private User updatedBy;

  @PrePersist
  void prePersist() {
    if (contentFormat == null) {
      contentFormat = PostContentFormat.PLAIN_TEXT;
    }
    if (contentText == null) {
      contentText = content;
    }
    if (contentHtml == null) {
      contentHtml = content;
    }
  }
}
