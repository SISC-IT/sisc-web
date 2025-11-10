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
import org.sejongisc.backend.board.entity.Board;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.domain.Page;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PostResponse {

  private UUID postId;
  private Board board;
  private User user;
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
