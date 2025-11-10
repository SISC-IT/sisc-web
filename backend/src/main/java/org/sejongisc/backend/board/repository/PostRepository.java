package org.sejongisc.backend.board.repository;

import java.util.UUID;
import org.sejongisc.backend.board.entity.Board;
import org.sejongisc.backend.board.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, UUID> {

  Page<Post> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
      String titleKeyword, String contentKeyword, Pageable pageable);

  Page<Post> findAllByBoard(Board board, Pageable pageable);

  @Query("SELECT p FROM Post p WHERE p.board = :board AND (" +
         "LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
         "LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
  Page<Post> searchByBoardAndKeyword(
      @Param("board") Board board,
      @Param("keyword") String keyword,
      Pageable pageable);
}
