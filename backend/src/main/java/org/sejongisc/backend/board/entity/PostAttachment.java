package org.sejongisc.backend.board.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.user.entity.User;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostAttachment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID postAttachmentId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Post post;

  @Column(nullable = false)
  private String savedFilename;

  @Column(nullable = false)
  private String originalFilename;

  @Column(nullable = false)
  private String filePath;
}
