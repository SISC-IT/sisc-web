package org.sejongisc.backend.board.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.board.dto.BoardRequest;
import org.sejongisc.backend.board.dto.CommentResponse;
import org.sejongisc.backend.board.dto.PostAttachmentResponse;
import org.sejongisc.backend.board.dto.PostRequest;
import org.sejongisc.backend.board.dto.PostResponse;
import org.sejongisc.backend.board.entity.Board;
import org.sejongisc.backend.board.entity.Comment;
import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.board.entity.PostAttachment;
import org.sejongisc.backend.board.repository.BoardRepository;
import org.sejongisc.backend.board.repository.CommentRepository;
import org.sejongisc.backend.board.repository.PostAttachmentRepository;
import org.sejongisc.backend.board.repository.PostBookmarkRepository;
import org.sejongisc.backend.board.repository.PostLikeRepository;
import org.sejongisc.backend.board.repository.PostRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final CommentRepository commentRepository;
  private final PostLikeRepository postLikeRepository;
  private final PostBookmarkRepository postBookmarkRepository;
  private final PostAttachmentRepository postAttachmentRepository;
  private final BoardRepository boardRepository;
  private final FileUploadService fileUploadService;

  // 게시물 작성
  @Override
  @Transactional
  public void savePost(PostRequest request, UUID userId) {
    Board board = boardRepository.findById(request.getBoardId())
        .orElseThrow(() -> new CustomException(ErrorCode.BOARD_NOT_FOUND));

    // 최상위 게시판일 경우
    if (board.getParentBoard() == null) {
      log.error("최상위 게시판에는 게시물을 작성할 수 없습니다. boardId: {}", request.getBoardId());
      throw new CustomException(ErrorCode.INVALID_BOARD_TYPE);
    }

    Post post = Post.builder()
        .user(userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)))
        .board(board)
        .title(request.getTitle())
        .content(request.getContent())
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

    // 기존 파일 조회 및 삭제
    List<PostAttachment> existingAttachments = postAttachmentRepository.findAllByPostPostId(postId);

    // DB에서 첨부파일 정보 일괄 삭제
    postAttachmentRepository.deleteAllByPostPostId(postId);

    // 물리적 파일 삭제
    for (PostAttachment attachment : existingAttachments) {
      fileUploadService.delete(attachment.getSavedFilename());
    }

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

    // DB에서 첨부파일 정보 삭제
    postAttachmentRepository.deleteAllByPostPostId(postId);

    // 물리적 파일 삭제
    for (PostAttachment attachment : attachments) {
      fileUploadService.delete(attachment.getSavedFilename());
    }

    // 댓글 삭제
    commentRepository.deleteAllByPostPostId(post.getPostId());
    postLikeRepository.deleteAllByPostPostId(post.getPostId());
    postBookmarkRepository.deleteAllByPostPostId(post.getPostId());

    // 게시물 삭제
    postRepository.delete(post);
  }

  // 게시물 조회 (해당 게시판의 게시물)
  @Override
  @Transactional(readOnly = true)
  public Page<PostResponse> getPosts(UUID boardId, int pageNumber, int pageSize) {
    Pageable pageable = PageRequest.of(
        pageNumber,
        pageSize,
        Sort.by(Direction.DESC, "createdDate")
    );

    // 게시판 조회
    Board board = boardRepository.findById(boardId)
        .orElseThrow(() -> new CustomException(ErrorCode.BOARD_NOT_FOUND));

    // 해당 게시판의 게시물 조회
    Page<Post> posts = postRepository.findAllByBoard(board, pageable);

    return posts.map(this::mapToPostResponse);
  }

  // 게시물 검색 (제목/내용)
  @Override
  @Transactional(readOnly = true)
  public Page<PostResponse> searchPosts(UUID boardId, String keyword, int pageNumber, int pageSize) {
    Pageable pageable = PageRequest.of(
        pageNumber,
        pageSize,
        Sort.by(Direction.DESC, "createdDate")
    );

    // 게시판 조회
    Board board = boardRepository.findById(boardId)
        .orElseThrow(() -> new CustomException(ErrorCode.BOARD_NOT_FOUND));

    // 해당 키워드가 들어간 게시물 검색
    Page<Post> posts = postRepository.findAllByBoardAndTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
        board, keyword, keyword, pageable);

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
        .board(post.getBoard())
        .user(post.getUser())
        .title(post.getTitle())
        .content(post.getContent())
        .bookmarkCount(post.getBookmarkCount())
        .likeCount(post.getLikeCount())
        .commentCount(post.getCommentCount())
        .createdDate(post.getCreatedDate())
        .updatedDate(post.getUpdatedDate())
        .comments(commentResponses)
        .attachments(attachmentResponses)
        .build();
  }

  // 게시판 생성
  @Transactional
  public void createBoard(BoardRequest request) {
    Board board;
    // 하위 게시판인 경우
    if (request.getParentBoardId() != null) {
      Board parentBoard = Board.builder()
          .boardId(request.getParentBoardId())
          .build();

      board = Board.builder()
          .boardName(request.getBoardName())
          .parentBoard(parentBoard)
          .build();
    } else {
      // 상위 게시판인 경우
      board = Board.builder()
          .boardName(request.getBoardName())
          .parentBoard(null)
          .build();
    }

    boardRepository.save(board);
  }

  private PostResponse mapToPostResponse(Post post) {
    return PostResponse.builder()
        .postId(post.getPostId())
        .user(post.getUser())
        .board(post.getBoard())
        .title(post.getTitle())
        .content(post.getContent())
        .bookmarkCount(post.getBookmarkCount())
        .likeCount(post.getLikeCount())
        .commentCount(post.getCommentCount())
        .createdDate(post.getCreatedDate())
        .updatedDate(post.getUpdatedDate())
        .build();
  }
}
