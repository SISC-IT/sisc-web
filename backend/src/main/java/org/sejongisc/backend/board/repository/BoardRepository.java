package org.sejongisc.backend.board.repository;

import org.sejongisc.backend.board.domain.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BoardRepository extends JpaRepository<Board, UUID> {
}
