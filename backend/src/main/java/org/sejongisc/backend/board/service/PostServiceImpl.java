package org.sejongisc.backend.board.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.board.dto.BoardRequest;
import org.sejongisc.backend.board.dto.BoardResponse;
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
import org.sejongisc.backend.user.dto.UserInfoResponse;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
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

  // 게시판 삭제
  @Override
  @Transactional
  public void deleteBoard(UUID boardId, UUID boardUserId) {
    User user = userRepository.findById(boardUserId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    if(!user.getRole().equals(Role.PRESIDENT)){
      throw new CustomException(ErrorCode.INVALID_BOARD_OWNER);
    }
    //상위 게시판이면 하위 게시판 목록을 조회
    // 1. 부모 + 자식 boardId 목록 만들기
    List<UUID> targetBoardIds = Stream.concat(
            Stream.of(boardId), // 자신 포함
            boardRepository.findAllByParentBoard_BoardId(boardId).stream()
                    .map(Board::getBoardId)
    ).toList();

    // 2. 각 boardId마다 postId/userId 조회해서 삭제
    targetBoardIds.stream()
            .flatMap(id -> postRepository.findPostIdAndUserIdByBoardId(id).stream())
            .forEach(row -> deletePost(row.getPostId(), row.getUserId()));
    targetBoardIds.forEach(boardRepository::deleteById);
    return;
  }

  // 게시물 조회 (해당 게시판의 게시물)
  @Override
  @Transactional(readOnly = true)
  public Page<PostResponse> getPosts(UUID boardId, UUID userId, int pageNumber, int pageSize) {
    Pageable pageable = PageRequest.of(
        pageNumber,
        pageSize,
        Sort.by(Direction.DESC, "createdDate")
    );

    // 게시판 조회
    Board board = boardRepository.findById(boardId)
        .orElseThrow(() -> new CustomException(ErrorCode.BOARD_NOT_FOUND));

    // 유저 조회 (좋아요/북마크 여부 확인용)
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    // 해당 게시판의 게시물 조회
    Page<Post> posts = postRepository.findAllByBoard(board, pageable);

    return posts.map(post -> mapToPostResponse(post, user));
  }

  // 게시물 검색 (제목/내용)
  @Override
  @Transactional(readOnly = true)
  public Page<PostResponse> searchPosts(UUID boardId, UUID userId, String keyword, int pageNumber, int pageSize) {
    Pageable pageable = PageRequest.of(
        pageNumber,
        pageSize,
        Sort.by(Direction.DESC, "createdDate")
    );

    // 게시판 조회
    Board board = boardRepository.findById(boardId)
        .orElseThrow(() -> new CustomException(ErrorCode.BOARD_NOT_FOUND));

    // 해당 키워드가 들어간 게시물 검색
    Page<Post> posts = postRepository.searchByBoardAndKeyword(
        board, keyword, pageable);

    // 유저 조회
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    return posts.map(post -> mapToPostResponse(post, user));
  }

  // 게시물 상세 조회
  @Override
  @Transactional(readOnly = true)
  public PostResponse getPostDetail(UUID postId, UUID userId, int pageNumber, int pageSize) {
    // 게시물 조회
    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

    // 댓글 페이징을 위한 Pageable 객체 생성
    Pageable pageable = PageRequest.of(
        pageNumber,
        pageSize,
        Sort.by(Sort.Direction.ASC, "createdDate")
    );

    // 부모 댓글만 페이징하여 조회
    Page<Comment> parentComments = commentRepository
        .findAllByPostPostIdAndParentCommentIsNull(postId, pageable);

    // 부모 댓글을 CommentResponse DTO로 변환
    Page<CommentResponse> commentResponses = parentComments.map(parent -> {
      // 해당 부모 댓글의 자식 댓글 목록을 조회
      List<Comment> childComments = commentRepository.findByParentComment(parent);

      // 자식 댓글 목록을 CommentResponse DTO 리스트로 변환
      List<CommentResponse> replyResponses = childComments.stream()
          .map(CommentResponse::from)
          .toList();

      // 부모 댓글 DTO를 생성하며, 자식 DTO 리스트를 주입
      return CommentResponse.from(parent, replyResponses);
    });

    // 첨부 파일 조회
    List<PostAttachmentResponse> attachmentResponses = postAttachmentRepository.findAllByPostPostId(postId)
        .stream()
        .map(PostAttachmentResponse::of)
        .toList();

    // 유저 조회
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    return getCommonPostBuilder(post, user)
        .comments(commentResponses)
        .attachments(attachmentResponses)
        .build();
  }

  // 게시판 생성
  @Override
  @Transactional
  public void createBoard(BoardRequest request, UUID userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    Board board;
    // 하위 게시판인 경우
    if (request.getParentBoardId() != null) {
      Board parentBoard = boardRepository.findById(request.getParentBoardId())
          .orElseThrow(() -> new CustomException(ErrorCode.BOARD_NOT_FOUND));

      board = Board.builder()
          .boardName(request.getBoardName())
          .createdBy(user)
          .parentBoard(parentBoard)
          .build();
    } else {
      // 상위 게시판인 경우
      board = Board.builder()
          .boardName(request.getBoardName())
          .createdBy(user)
          .parentBoard(null)
          .build();
    }

    boardRepository.save(board);
  }

  // 부모 게시판 조회
  @Override
  @Transactional(readOnly = true)
  public List<BoardResponse> getParentBoards() {
    List<Board> parentBoards = boardRepository.findAllByParentBoardIsNull();

    return parentBoards.stream()
        .map(BoardResponse::from)
        .toList();
  }

  // 하위 게시판 조회
  @Transactional(readOnly = true)
  public List<BoardResponse> getChildBoards() {
    List<Board> childBoards = boardRepository.findAllByParentBoardIsNotNull();

    return childBoards.stream()
        .map(BoardResponse::from)
        .toList();
  }

  private PostResponse.PostResponseBuilder getCommonPostBuilder(Post post, User user) {
    return PostResponse.builder()
        .postId(post.getPostId())
        .user(UserInfoResponse.from(post.getUser()))
        .board(BoardResponse.from(post.getBoard()))
        .title(post.getTitle())
        .content(post.getContent())
        .bookmarkCount(post.getBookmarkCount())
        .likeCount(post.getLikeCount())
        .commentCount(post.getCommentCount())
        .createdDate(post.getCreatedDate())
        .updatedDate(post.getUpdatedDate())
        .isLiked(postLikeRepository.existsByUserUserIdAndPostPostId(user.getUserId(), post.getPostId()))
        .isBookmarked(postBookmarkRepository.existsByUserUserIdAndPostPostId(user.getUserId(), post.getPostId()));
  }

  private PostResponse mapToPostResponse(Post post, User user) {
    return getCommonPostBuilder(post, user)
        .build();
  }
}