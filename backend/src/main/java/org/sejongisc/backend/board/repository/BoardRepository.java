package org.sejongisc.backend.board.repository;

import java.util.List;
import java.util.UUID;
import org.sejongisc.backend.board.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardRepository extends JpaRepository<Board, UUID> {

  List<Board> findAllByParentBoardIsNull();
  List<Board> findAllByParentBoardIsNotNull();
  List<Board> findAllByParentBoard_BoardId(UUID parentBoardId);

}
