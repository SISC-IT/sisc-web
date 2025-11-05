package org.sejongisc.backend.board.repository;

import org.sejongisc.backend.board.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

  List<Comment> findByPostId(UUID postId);

  List<Comment> findAllByPostId(UUID postId);

  Page<Comment> findAllByPostId(UUID postId, Pageable pageable);

  List<Comment> findByParentId(UUID parentId);

  @Transactional
  void deleteAllByPostId(UUID postId);
}
