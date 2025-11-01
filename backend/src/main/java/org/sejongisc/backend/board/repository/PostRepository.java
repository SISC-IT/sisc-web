package org.sejongisc.backend.board.repository;

import org.sejongisc.backend.board.domain.Post;
import org.sejongisc.backend.board.domain.PostType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findByBoardId(UUID boardId);
    List<Post> findByBoardIdAndPostType(UUID boardId, PostType postType);
    List<Post> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(String titleKeyword, String contentKeyword);
}
