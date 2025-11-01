package org.sejongisc.backend.board.repository;

import org.sejongisc.backend.board.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);
    Optional<PostLike> findByUserIdAndPostId(UUID userId, UUID postId);
}
