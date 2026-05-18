package org.sejongisc.backend.board.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.sejongisc.backend.board.entity.PostMedia;
import org.sejongisc.backend.board.entity.PostMediaType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostMediaRepository extends JpaRepository<PostMedia, UUID> {

  List<PostMedia> findAllByMediaIdIn(Collection<UUID> mediaIds);

  List<PostMedia> findAllByPostPostId(UUID postId);

  List<PostMedia> findAllByPostPostIdAndMediaTypeOrderBySortOrderAscCreatedDateAsc(
      UUID postId,
      PostMediaType mediaType
  );

  void deleteAllByPostPostId(UUID postId);
}
