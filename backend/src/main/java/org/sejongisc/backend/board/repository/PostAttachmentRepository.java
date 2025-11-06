package org.sejongisc.backend.board.repository;

import org.sejongisc.backend.board.entity.PostAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, UUID> {

  List<PostAttachment> findAllByPostId(UUID postId);

  void deleteAllByPostId(UUID postId);
}
