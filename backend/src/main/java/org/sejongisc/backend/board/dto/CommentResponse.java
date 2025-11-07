package org.sejongisc.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.sejongisc.backend.board.entity.Comment;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class CommentResponse {
    private UUID commentId;
    private UUID postId;
    private String content;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

  public static CommentResponse of(Comment comment) {
    return CommentResponse.builder()
        .commentId(comment.getCommentId())
        .postId(comment.getPost().getPostId())
        .content(comment.getContent())
        .createdDate(comment.getCreatedDate())
        .updatedDate(comment.getUpdatedDate())
        .build();
  }
}
