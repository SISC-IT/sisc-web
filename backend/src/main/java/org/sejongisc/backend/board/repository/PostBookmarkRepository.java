package org.sejongisc.backend.board.repository;

import org.sejongisc.backend.board.domain.PostBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PostBookmarkRepository extends JpaRepository<PostBookmark, UUID> {
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);
    Optional<PostBookmark> findByUserIdAndPostId(UUID userId, UUID postId);
}
