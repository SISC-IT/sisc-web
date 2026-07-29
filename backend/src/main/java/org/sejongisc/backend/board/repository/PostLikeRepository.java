package org.sejongisc.backend.board.repository;

import java.util.List;
import org.sejongisc.backend.board.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

  boolean existsByUserUserIdAndPostPostId(UUID userId, UUID postId);

  Optional<PostLike> findByPostPostIdAndUserUserId(UUID postId, UUID userId);

  List<PostLike> findAllByPostPostId(UUID postId);

  @Transactional
  void deleteAllByPostPostId(UUID postId);
}
