package org.sejongisc.backend.board.dao;

import org.sejongisc.backend.board.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findByTitleContainingOrContentContaining(String title, String content);
}
