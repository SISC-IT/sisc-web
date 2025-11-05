package org.sejongisc.backend.board.repository;

import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.board.entity.PostType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findByBoardId(UUID boardId);
    List<Post> findByBoardIdAndPostType(UUID boardId, PostType postType);
    Page<Post> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
        String titleKeyword, String contentKeyword, Pageable pageable);
}
