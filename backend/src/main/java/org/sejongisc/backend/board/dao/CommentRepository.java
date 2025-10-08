package org.sejongisc.backend.board.dao;

import org.sejongisc.backend.board.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
}
