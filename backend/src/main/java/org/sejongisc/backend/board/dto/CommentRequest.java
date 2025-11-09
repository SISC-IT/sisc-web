package org.sejongisc.backend.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class CommentRequest {

  @NotNull
  private UUID postId;

  @NotBlank(message = "댓글 내용은 필수 항목입니다.")
  private String content;
}
