package org.sejongisc.backend.board.service;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.entity.*;
import org.sejongisc.backend.board.dto.*;
import org.sejongisc.backend.board.repository.*;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class PostServiceImpl implements PostService {

  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final CommentRepository commentRepository;
  private final PostLikeRepository postLikeRepository;
  private final PostBookmarkRepository postBookmarkRepository;
  private final PostAttachmentRepository postAttachmentRepository;
  private final FileUploadService fileUploadService;

  // 게시물 작성
  @Override
  @Transactional
  public void savePost(PostRequest request, UUID userId) {
    Post post = Post.builder()
        .user(userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)))
        .boardType(request.getBoardType())
        .title(request.getTitle())
        .content(request.getContent())
        .postType(request.getPostType())
        .build();

    post = postRepository.save(post);

    // 첨부파일 저장
    List<MultipartFile> files = request.getFiles();
    if (files != null && !files.isEmpty()) {
      for (MultipartFile file : files) {
        if (file != null && !file.isEmpty()) {
          String savedFilename = fileUploadService.store(file);
          String filePath = fileUploadService.getRootLocation().resolve(savedFilename).toString();

          PostAttachment attachment = PostAttachment.builder()
              .post(post)
              .savedFilename(savedFilename)
              .originalFilename(file.getOriginalFilename())
              .filePath(filePath)
              .build();
          postAttachmentRepository.save(attachment);
        }
      }
    }
  }

  // 게시물 수정
  @Override
  @Transactional
  public void updatePost(PostRequest request, UUID postId, UUID userId) {
    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

    if (!post.getUser().getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.INVALID_POST_OWNER);
    }

    post.setTitle(request.getTitle());
    post.setContent(request.getContent());
    post.setPostType(request.getPostType());

    // 기존 파일 조회 및 삭제
    List<PostAttachment> existingAttachments = postAttachmentRepository.findAllByPostPostId(postId);
    for (PostAttachment attachment : existingAttachments) {
      fileUploadService.delete(attachment.getSavedFilename());
    }
    // DB에서 첨부파일 정보 일괄 삭제
    postAttachmentRepository.deleteAllByPostPostId(postId);

    // 새 파일 저장
    List<MultipartFile> files = request.getFiles();
    if (files != null && !files.isEmpty()) {
      for (MultipartFile file : files) {
        if (file != null && !file.isEmpty()) {
          String savedFilename = fileUploadService.store(file);
          String filePath = fileUploadService.getRootLocation().resolve(savedFilename).toString();

          PostAttachment attachment = PostAttachment.builder()
              .post(post)
              .savedFilename(savedFilename)
              .originalFilename(file.getOriginalFilename())
              .filePath(filePath)
              .build();
          postAttachmentRepository.save(attachment);
        }
      }
    }
  }

  // 게시물 삭제
  @Override
  @Transactional
  public void deletePost(UUID postId, UUID userId) {
    // 게시물 조회
    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

    // 작성자 확인
    if (!post.getUser().getUserId().equals(userId)) {
      throw new CustomException(ErrorCode.INVALID_POST_OWNER);
    }

    // DB에서 첨부파일 정보 조회
    List<PostAttachment> attachments = postAttachmentRepository.findAllByPostPostId(postId);

    // 물리적 파일 삭제
    for (PostAttachment attachment : attachments) {
      fileUploadService.delete(attachment.getSavedFilename());
    }

    // DB에서 첨부파일 정보 삭제
    postAttachmentRepository.deleteAllByPostPostId(postId);

    // 댓글 삭제
    commentRepository.deleteAllByPostPostId(post.getPostId());
    postLikeRepository.deleteAllByPostPostId(post.getPostId());
    postBookmarkRepository.deleteAllByPostPostId(post.getPostId());

    // 게시물 삭제
    postRepository.delete(post);
  }

  // 게시물 조회 (전체 | 금융 IT | 자산 운용)
  @Override
  @Transactional(readOnly = true)
  public Page<PostResponse> getPosts(BoardType boardType, int pageNumber, int pageSize) {
    Pageable pageable = PageRequest.of(
        pageNumber,
        pageSize,
        Sort.by(Direction.DESC, "createdDate")
    );

    // 게시판 타입에 따른 게시물 조회
    Page<Post> posts = postRepository.findAllByBoardType(boardType, pageable);

    return posts.map(this::mapToPostResponse);
  }

  // 게시물 검색 (제목/내용)
  @Transactional(readOnly = true)
  @Override
  public Page<PostResponse> searchPosts(String keyword, int pageNumber, int pageSize) {
    Pageable pageable = PageRequest.of(
        pageNumber,
        pageSize,
        Sort.by(Direction.DESC, "createdDate")
    );

    // 해당 키워드가 들어간 게시물 검색
    Page<Post> posts = postRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
        keyword, keyword, pageable);

    return posts.map(this::mapToPostResponse);
  }

  // 게시물 상세 조회
  @Override
  @Transactional(readOnly = true)
  public PostResponse getPostDetail(UUID postId, int pageNumber, int pageSize) {
    // 게시물 조회
    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

    // 댓글 페이징을 위한 Pageable 객체 생성
    Pageable pageable = PageRequest.of(
        pageNumber,
        pageSize,
        Sort.by(Sort.Direction.ASC, "createdDate")
    );

    // 해당 게시물의 댓글 목록을 '페이징'하여 조회
    Page<Comment> comments = commentRepository.findAllByPostPostId(postId, pageable);

    // Page<Comment> -> Page<CommentResponse> DTO로 변환
    Page<CommentResponse> commentResponses = comments.map(CommentResponse::of);

    // 첨부 파일 조회
    List<PostAttachmentResponse> attachmentResponses = postAttachmentRepository.findAllByPostPostId(postId)
        .stream()
        .map(PostAttachmentResponse::of)
        .toList();

    // PostResponse DTO를 직접 빌드하여 반환
    return PostResponse.builder()
        .postId(post.getPostId())
        .boardType(post.getBoardType())
        .user(post.getUser())
        .title(post.getTitle())
        .content(post.getContent())
        .postType(post.getPostType())
        .bookmarkCount(post.getBookmarkCount())
        .likeCount(post.getLikeCount())
        .commentCount(post.getCommentCount())
        .createdDate(post.getCreatedDate())
        .updatedDate(post.getUpdatedDate())
        .comments(commentResponses)
        .attachments(attachmentResponses)
        .build();
  }

  // 댓글 작성
  @Override
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

    // comment 엔티티 저장
    Comment comment = Comment.builder()
        .post(post)
        .user(user)
        .content(request.getContent())
        .build();

    commentRepository.save(comment);

    // 게시글의 댓글 수 1 증가
    post.setCommentCount(post.getCommentCount() + 1);
  }

  // 댓글 수정
  @Override
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
  @Override
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

    // 게시글의 댓글 수 1 감소
    Post post = postRepository.findById(comment.getPost().getPostId())
        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

    post.setCommentCount(post.getCommentCount() - 1);

    // comment 삭제
    commentRepository.delete(comment);
  }

  // 좋아요 등록/삭제
  @Override
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
  @Override
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

  private PostResponse mapToPostResponse(Post post) {
    return PostResponse.builder()
        .postId(post.getPostId())
        .user(post.getUser())
        .boardType(post.getBoardType())
        .title(post.getTitle())
        .content(post.getContent())
        .postType(post.getPostType())
        .bookmarkCount(post.getBookmarkCount())
        .likeCount(post.getLikeCount())
        .commentCount(post.getCommentCount())
        .createdDate(post.getCreatedDate())
        .updatedDate(post.getUpdatedDate())
        .build();
  }
}
