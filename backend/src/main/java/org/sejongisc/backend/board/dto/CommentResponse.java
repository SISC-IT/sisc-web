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
  private boolean anonymous;
  private LocalDateTime createdDate;
  private LocalDateTime updatedDate;
  private UUID parentCommentId;
  private List<CommentResponse> replies;

  private static UserInfoResponse getCommentUser(Comment comment) {
    if (comment.isAnonymous()) {
      return new UserInfoResponse(null, "익명", null, null, null, null, List.of());
    }
    return UserInfoResponse.from(comment.getUser());
  }

  public static CommentResponse from(Comment comment) {
    UUID parentId = (comment.getParentComment() != null)
        ? comment.getParentComment().getCommentId()
        : null;

    return CommentResponse.builder()
        .commentId(comment.getCommentId())
        .user(getCommentUser(comment))
        .postId(comment.getPost().getPostId())
        .content(comment.getContent())
        .anonymous(comment.isAnonymous())
        .createdDate(comment.getCreatedDate())
        .updatedDate(comment.getUpdatedDate())
        .parentCommentId(parentId)
        .build();
  }

  public static CommentResponse from(Comment comment, List<CommentResponse> replies) {
    UUID parentCommentId = (comment.getParentComment() != null)
        ? comment.getParentComment().getCommentId()
        : null;

    return CommentResponse.builder()
        .commentId(comment.getCommentId())
        .user(getCommentUser(comment))
        .postId(comment.getPost().getPostId())
        .content(comment.getContent())
        .anonymous(comment.isAnonymous())
        .createdDate(comment.getCreatedDate())
        .updatedDate(comment.getUpdatedDate())
        .parentCommentId(parentCommentId)
        .replies(replies)
        .build();
  }
}
