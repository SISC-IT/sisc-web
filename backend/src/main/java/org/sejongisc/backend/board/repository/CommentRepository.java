package org.sejongisc.backend.board.repository;

import org.sejongisc.backend.board.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

  List<Comment> findByPostPostId(UUID postId);

  List<Comment> findAllByPostPostId(UUID postId);

  Page<Comment> findAllByPostPostId(UUID postId, Pageable pageable);

  @Transactional
  void deleteAllByPostPostId(UUID postId);

  List<Comment> findByParentComment(Comment parentComment);

  Page<Comment> findAllByPostPostIdAndParentCommentIsNull(UUID postId, Pageable pageable);
}
