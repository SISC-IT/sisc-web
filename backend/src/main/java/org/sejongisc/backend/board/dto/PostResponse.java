package org.sejongisc.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.sejongisc.backend.board.entity.BoardType;
import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.board.entity.PostType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
  private BoardType boardType;
  private User user;
  private String title;
  private String content;
  private PostType postType;
  private Integer bookmarkCount;
  private Integer likeCount;
  private Integer commentCount;
  private LocalDateTime createdDate;
  private LocalDateTime updatedDate;
  private Page<CommentResponse> comments;
  private List<PostAttachmentResponse> attachments;
}
