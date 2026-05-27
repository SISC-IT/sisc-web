package org.sejongisc.backend.board.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.sejongisc.backend.board.entity.PostContentFormat;
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
  private PostContentFormat contentFormat;
  private JsonNode contentJson;
  private String contentHtml;
  private String contentText;
  private boolean anonymous;
  private boolean publicVisible;
  private LocalDateTime publicPublishedAt;
  private Integer bookmarkCount;
  private Integer likeCount;
  private Integer commentCount;
  private Boolean isLiked;
  private Boolean isBookmarked;
  private LocalDateTime createdDate;
  private LocalDateTime updatedDate;
  private Page<CommentResponse> comments;
  private List<PostAttachmentResponse> attachments;
  private List<PostMediaResponse> inlineImages;
  private List<PostMediaResponse> fileAttachments;
}
