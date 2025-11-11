package org.sejongisc.backend.board.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.board.entity.PostAttachment;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostAttachmentResponse {
  private UUID postAttachmentId;
  private String originalFilename;
  private String filePath;

  public static PostAttachmentResponse of(PostAttachment attachment) {
    return PostAttachmentResponse.builder()
        .postAttachmentId(attachment.getPostAttachmentId())
        .originalFilename(attachment.getOriginalFilename())
        .filePath(attachment.getFilePath())
        .build();
  }
}
