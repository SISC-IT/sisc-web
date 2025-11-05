package org.sejongisc.backend.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.sejongisc.backend.board.entity.BoardType;
import org.sejongisc.backend.board.entity.PostType;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class PostRequest {

  @NotNull(message = "게시판 타입을 선택해주세요.")
  private BoardType boardType;

  @NotBlank(message = "제목은 필수 항목입니다.")
  private String title;

  @NotBlank(message = "내용은 필수 항목입니다.")
  private String content;

  @NotNull(message = "게시글 타입을 선택해주세요.")
  private PostType postType;
}
