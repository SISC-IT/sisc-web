package org.sejongisc.backend.board.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.sejongisc.backend.user.dto.UserInfoResponse;
import org.springframework.data.domain.Page;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PostResponse {

  private UUID postId;
  private BoardResponse board;
  private UserInfoResponse user;
  private String title;
  private String content;
  private Integer bookmarkCount;
  private Integer likeCount;
  private Integer commentCount;
  private LocalDateTime createdDate;
  private LocalDateTime updatedDate;
  private Page<CommentResponse> comments;
  private List<PostAttachmentResponse> attachments;
}
