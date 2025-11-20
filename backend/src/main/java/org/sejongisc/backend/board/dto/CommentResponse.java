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
import org.sejongisc.backend.board.entity.Comment;
import org.sejongisc.backend.user.dto.UserInfoResponse;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class CommentResponse {
    private UUID commentId;
    private UserInfoResponse user;
    private UUID postId;
    private String content;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
    private List<CommentResponse> replies;

  public static CommentResponse from(Comment comment) {
    return CommentResponse.builder()
        .commentId(comment.getCommentId())
        .user(UserInfoResponse.from(comment.getUser()))
        .postId(comment.getPost().getPostId())
        .content(comment.getContent())
        .createdDate(comment.getCreatedDate())
        .updatedDate(comment.getUpdatedDate())
        .build();
  }

  public static CommentResponse from(Comment comment, List<CommentResponse> replies) {
    return CommentResponse.builder()
        .commentId(comment.getCommentId())
        .user(UserInfoResponse.from(comment.getUser()))
        .postId(comment.getPost().getPostId())
        .content(comment.getContent())
        .createdDate(comment.getCreatedDate())
        .updatedDate(comment.getUpdatedDate())
        .replies(replies)
        .build();
  }
}
