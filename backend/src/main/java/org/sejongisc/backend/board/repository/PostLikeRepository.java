package org.sejongisc.backend.board.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.sejongisc.backend.board.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

  boolean existsByUserUserIdAndPostPostId(UUID userId, UUID postId);

  Optional<PostLike> findByPostPostIdAndUserUserId(UUID postId, UUID userId);

  List<PostLike> findAllByPostPostId(UUID postId);

  @Query(
      value = """
      select pg_advisory_xact_lock(
        hashtext(cast(:postId as text)),
        hashtext(cast(:userId as text))
      )
      """,
      nativeQuery = true
  )
  Object acquirePostUserLikeToggleLock(
      @Param("postId") UUID postId,
      @Param("userId") UUID userId
  );

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value = """
      insert into post_like (post_like_id, post_id, user_id, created_date, updated_date)
      values (:postLikeId, :postId, :userId, now(), now())
      on conflict (post_id, user_id) do nothing
      """,
      nativeQuery = true
  )
  int insertIgnore(
      @Param("postLikeId") UUID postLikeId,
      @Param("postId") UUID postId,
      @Param("userId") UUID userId
  );

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      delete from PostLike pl
      where pl.post.postId = :postId
        and pl.user.userId = :userId
      """)
  int deleteByPostIdAndUserId(
      @Param("postId") UUID postId,
      @Param("userId") UUID userId
  );

  @Transactional
  void deleteAllByPostPostId(UUID postId);
}
