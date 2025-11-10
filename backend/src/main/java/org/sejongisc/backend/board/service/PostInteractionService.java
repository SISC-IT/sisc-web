package org.sejongisc.backend.board.service;

import jakarta.persistence.OptimisticLockException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.dto.CommentRequest;
import org.sejongisc.backend.board.entity.Comment;
import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.board.entity.PostBookmark;
import org.sejongisc.backend.board.entity.PostLike;
import org.sejongisc.backend.board.repository.CommentRepository;
import org.sejongisc.backend.board.repository.PostBookmarkRepository;
import org.sejongisc.backend.board.repository.PostLikeRepository;
import org.sejongisc.backend.board.repository.PostRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PostInteractionService {

  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final CommentRepository commentRepository;
  private final PostLikeRepository postLikeRepository;
  private final PostBookmarkRepository postBookmarkRepository;

  // 댓글 작성
  @Transactional
  @Retryable(
      value = { ObjectOptimisticLockingFailureException.class, OptimisticLockException.class },
      maxAttempts = 5,
      backoff = @Backoff(delay = 100)
  )
  public void createComment(CommentRequest request, UUID userId) {
    // 게시글 조회
    Post post = postRepository.findById(request.getPostId())
        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

    // 작성자 조회
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    // 부모 댓글 조회 (대댓글인 경우)
    Comment parentComment = null;
    if (request.getParentCommentId() != null) {
      parentComment = commentRepository.findById(request.getParentCommentId())
          .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

      // 부모 댓글이 해당 게시글에 속하는지 확인
      if (!parentComment.getPost().getPostId().equals(post.getPostId())) {
        throw new CustomException(ErrorCode.INVALID_PARENT_COMMENT);
      }

      if (parentComment.getParentComment() != null) {
        throw new CustomException(ErrorCode.ALREADY_CHILD_COMMENT);
      }
    }

    // comment 엔티티 저장
    Comment comment = Comment.builder()
        .post(post)
        .user(user)
        .content(request.getContent())
        .parentComment(parentComment)
        .build();

    commentRepository.save(comment);

    // 게시글의 댓글 수 1 증가
    post.setCommentCount(post.getCommentCount() + 1);
  }

  // 댓글 수정
  @Transactional
  public void updateComment(CommentRequest request, UUID commentId, UUID userId) {
    // comment 조회
    Comment comment = commentRepository.findById(commentId)
        .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

    // 작성자 확인
    if (!comment.getUser().getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.INVALID_COMMENT_OWNER);
    }

    // 내용 업데이트
    comment.setContent(request.getContent());
  }

  // 댓글 삭제
  @Transactional
  @Retryable(
      value = { ObjectOptimisticLockingFailureException.class, OptimisticLockException.class },
      maxAttempts = 5,
      backoff = @Backoff(delay = 100)
  )
  public void deleteComment(UUID commentId, UUID userId) {
    // comment 조회
    Comment comment = commentRepository.findById(commentId)
        .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

    // 관리자 확인
    boolean isAdmin = userRepository.findById(userId)
        .map(user -> user.getRole() == Role.PRESIDENT || user.getRole() == Role.VICE_PRESIDENT)
        .orElse(false);

    // 작성자 확인 (관리자는 통과)
    if (!comment.getUser().getUserId().equals(userId) && !isAdmin) {
      throw new CustomException(ErrorCode.INVALID_COMMENT_OWNER);
    }

    // 게시글 조회
    Post post = postRepository.findById(comment.getPost().getPostId())
        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

    // 자식 댓글 조회
    List<Comment> childComments = commentRepository.findByParentComment(comment);

    int totalDeletedCount = 0; // 삭제할 총 개수

    if (!childComments.isEmpty()) {
      // 자식 댓글 일괄 삭제 (쿼리 1번)
      commentRepository.deleteAll(childComments);

      totalDeletedCount = childComments.size(); // 자식 댓글 개수만큼 카운트
    }

    // 부모 댓글(본인) 삭제
    commentRepository.delete(comment);
    totalDeletedCount += 1; // 본인 개수 추가

    // Post 카운트 업데이트
    post.setCommentCount(post.getCommentCount() - totalDeletedCount);
  }

  // 좋아요 등록/삭제
  @Transactional
  @Retryable(
      value = { ObjectOptimisticLockingFailureException.class, OptimisticLockException.class },
      maxAttempts = 5,
      backoff = @Backoff(delay = 100)
  )
  public void toggleLike(UUID postId, UUID userId) {
    // 게시물 조회
    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

    // 유저 조회
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    // 이미 좋아요를 눌렀는지 확인
    Optional<PostLike> existingLike = postLikeRepository.findByPostPostIdAndUserUserId(postId, userId);

    if (existingLike.isPresent()) {
      // 좋아요가 이미 있으면 -> 삭제 (좋아요 취소)
      postLikeRepository.delete(existingLike.get());
      post.setLikeCount(post.getLikeCount() - 1); // Post 엔티티 카운트 감소
    } else {
      // 좋아요가 없으면 -> 생성 (좋아요)
      PostLike newLike = PostLike.builder()
          .post(post)
          .user(user)
          .build();
      postLikeRepository.save(newLike);
      post.setLikeCount(post.getLikeCount() + 1); // Post 엔티티 카운트 증가
    }
  }

  // 북마크 등록/삭제
  @Transactional
  @Retryable(
      value = { ObjectOptimisticLockingFailureException.class, OptimisticLockException.class },
      maxAttempts = 5,
      backoff = @Backoff(delay = 100)
  )
  public void toggleBookmark(UUID postId, UUID userId) {
    // 게시물 조회
    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

    // 유저 조회
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    // 이미 북마크를 했는지 확인
    Optional<PostBookmark> existingBookmark = postBookmarkRepository.findByPostPostIdAndUserUserId(postId, userId);

    if (existingBookmark.isPresent()) {
      // 북마크가 이미 있으면 -> 삭제 (북마크 취소)
      postBookmarkRepository.delete(existingBookmark.get());
      post.setBookmarkCount(post.getBookmarkCount() - 1); // Post 엔티티 카운트 감소
    } else {
      // 북마크가 없으면 -> 생성 (북마크)
      PostBookmark newBookmark = PostBookmark.builder()
          .post(post)
          .user(user)
          .build();
      postBookmarkRepository.save(newBookmark);
      post.setBookmarkCount(post.getBookmarkCount() + 1); // Post 엔티티 카운트 증가
    }
  }
}
