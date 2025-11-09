package org.sejongisc.backend.board.repository;

import java.util.UUID;
import org.sejongisc.backend.board.entity.Board;
import org.sejongisc.backend.board.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, UUID> {

  Page<Post> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
      String titleKeyword, String contentKeyword, Pageable pageable);

  Page<Post> findAllByBoard(Board board, Pageable pageable);

  Page<Post> findAllByBoardAndTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
      Board board, String titleKeyword, String contentKeyword, Pageable pageable);
}
