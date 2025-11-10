package org.sejongisc.backend.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class BoardRequest {

  @Schema(description = "게시판 이름")
  private String boardName;

  @Schema(description = "상위 게시판 ID (없으면 null)")
  private UUID parentBoardId;
}
