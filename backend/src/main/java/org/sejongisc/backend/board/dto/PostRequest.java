package org.sejongisc.backend.board.dto;

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

  private BoardType boardType;
  private String title;
  private String content;
  private PostType postType;
}
