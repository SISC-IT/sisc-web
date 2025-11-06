package org.sejongisc.backend.board.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

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

  @Column(nullable = false)
  private UUID postId;

  @Column(nullable = false)
  private String savedFilename;

  @Column(nullable = false)
  private String originalFilename;

  @Column(nullable = false)
  private String filePath;
}
