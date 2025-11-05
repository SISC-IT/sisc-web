package org.sejongisc.backend.board.repository;

import java.util.List;
import org.sejongisc.backend.board.entity.PostBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PostBookmarkRepository extends JpaRepository<PostBookmark, UUID> {

  boolean existsByUserIdAndPostId(UUID userId, UUID postId);

  Optional<PostBookmark> findByPostIdAndUserId(UUID postId, UUID userId);

  List<PostBookmark> findAllByPostId(UUID postId);

  void deleteAllByPostId(UUID postId);
}
