package org.sejongisc.backend.board.repository;

import java.util.List;
import org.sejongisc.backend.board.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

  boolean existsByUserIdAndPostId(UUID userId, UUID postId);

  Optional<PostLike> findByPostIdAndUserId(UUID postId, UUID userId);

  List<PostLike> findAllByPostId(UUID postId);

  void deleteAllByPostId(UUID postId);
}
