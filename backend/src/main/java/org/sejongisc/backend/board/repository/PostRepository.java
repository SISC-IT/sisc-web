package org.sejongisc.backend.board.repository;

import org.sejongisc.backend.board.domain.Post;
import org.sejongisc.backend.board.domain.PostType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    List<Post> findByBoardId(UUID boardId);

    List<Post> findByBoardIdAndPostType(UUID boardId, PostType postType);

    // ✅ 하나의 keyword 만 받도록 변경
    @Query("""
            SELECT p
            FROM Post p
            WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<Post> searchByKeyword(@Param("keyword") String keyword);
}
