package org.sejongisc.backend.board.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.sejongisc.backend.board.entity.Board;
import org.sejongisc.backend.user.dto.UserInfoResponse;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class BoardResponse {

  private UUID boardId;

  private String boardName;

  private UserInfoResponse createdBy;

  private UUID parentBoardId;

  public static BoardResponse from(Board board) {
    return BoardResponse.builder()
        .boardId(board.getBoardId())
        .boardName(board.getBoardName())
        .createdBy(UserInfoResponse.from(board.getCreatedBy()))
        .parentBoardId(board.getParentBoard() != null ? board.getParentBoard().getBoardId() : null)
        .build();
  }
}
