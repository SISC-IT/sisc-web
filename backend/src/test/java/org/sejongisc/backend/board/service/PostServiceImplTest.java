package org.sejongisc.backend.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.board.dto.BoardRequest;
import org.sejongisc.backend.board.dto.CommentResponse;
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
import org.sejongisc.backend.user.repository.UserRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

  @InjectMocks
  private PostServiceImpl postService;

  @Mock
  private UserRepository userRepository;
  @Mock
  private PostRepository postRepository;
  @Mock
  private CommentRepository commentRepository;
  @Mock
  private PostLikeRepository postLikeRepository;
  @Mock
  private PostBookmarkRepository postBookmarkRepository;
  @Mock
  private PostAttachmentRepository postAttachmentRepository;
  @Mock
  private BoardRepository boardRepository;
  @Mock
  private FileUploadService fileUploadService;

  // 테스트용 공유 객체
  private User mockUser;
  private Board mockBoard;
  private Board mockParentBoard;
  private Post mockPost;
  private UUID userId;
  private UUID boardId;
  private UUID postId;

  @BeforeEach
  void setUp() {
    // Mock 객체 기본 설정
    userId = UUID.randomUUID();
    boardId = UUID.randomUUID();
    postId = UUID.randomUUID();

    // DTO 변환(UserInfoResponse.from)시 NPE 방지를 위해 Role, Name, Email 등 필수 필드 세팅
    mockUser = User.builder()
        .userId(userId)
        .email("test@example.com")
        .name("Tester")
        .role(Role.TEAM_MEMBER) // 실제 프로젝트 Enum에 맞게 설정
        .build();

    mockParentBoard = Board.builder()
        .boardId(UUID.randomUUID())
        .boardName("Parent Board")
        .parentBoard(null)
        .createdBy(mockUser)
        .build();

    mockBoard = Board.builder()
        .boardId(boardId)
        .boardName("Child Board")
        .parentBoard(mockParentBoard)
        .createdBy(mockUser)
        .build();

    mockPost = Post.builder()
        .postId(postId)
        .user(mockUser)
        .board(mockBoard)
        .title("Test Title")
        .content("Test Content")
        .build();
  }

  @Test
  @DisplayName("게시물 생성 - 성공 (첨부파일 포함)")
  void savePost_Success() {
    // given
    MultipartFile mockFile = mock(MultipartFile.class);
    when(mockFile.getOriginalFilename()).thenReturn("test.txt");
    when(mockFile.isEmpty()).thenReturn(false);

    PostRequest request = PostRequest.builder()
        .boardId(boardId)
        .title("New Post")
        .content("New Content")
        .files(List.of(mockFile))
        .build();

    // Mocking
    when(boardRepository.findById(boardId)).thenReturn(Optional.of(mockBoard));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(fileUploadService.store(any(MultipartFile.class))).thenReturn("saved_filename.txt");
    when(fileUploadService.getRootLocation()).thenReturn(Paths.get("test/path"));
    when(postRepository.save(any(Post.class))).thenReturn(mockPost);

    // when
    postService.savePost(request, userId);

    // then
    verify(postRepository, times(1)).save(any(Post.class));
    verify(fileUploadService, times(1)).store(any(MultipartFile.class));
    verify(postAttachmentRepository, times(1)).save(any(PostAttachment.class));
  }

  @Test
  @DisplayName("게시물 생성 - 실패 (최상위 게시판에 작성 시도)")
  void savePost_Fail_ParentBoard() {
    // given
    PostRequest request = PostRequest.builder().boardId(mockParentBoard.getBoardId()).build();

    // Mocking
    when(boardRepository.findById(mockParentBoard.getBoardId())).thenReturn(Optional.of(mockParentBoard));

    // when & then
    CustomException exception = assertThrows(CustomException.class, () -> {
      postService.savePost(request, userId);
    });

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_BOARD_TYPE);
  }

  @Test
  @DisplayName("게시물 수정 - 성공")
  void updatePost_Success() {
    // given
    PostRequest request = PostRequest.builder()
        .title("Updated Title")
        .content("Updated Content")
        .files(Collections.emptyList())
        .build();

    PostAttachment oldAttachment = PostAttachment.builder().savedFilename("old_file.txt").build();

    // Mocking
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));
    when(postAttachmentRepository.findAllByPostPostId(postId)).thenReturn(List.of(oldAttachment));

    // when
    postService.updatePost(request, postId, userId);

    // then
    verify(postAttachmentRepository).deleteAllByPostPostId(postId);
    verify(fileUploadService).delete("old_file.txt");
    assertThat(mockPost.getTitle()).isEqualTo("Updated Title");
    assertThat(mockPost.getContent()).isEqualTo("Updated Content");
  }

  @Test
  @DisplayName("게시물 수정 - 실패 (작성자 불일치)")
  void updatePost_Fail_InvalidOwner() {
    // given
    UUID otherUserId = UUID.randomUUID();
    PostRequest request = PostRequest.builder().build();

    // Mocking
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));

    // when & then
    CustomException exception = assertThrows(CustomException.class, () -> {
      postService.updatePost(request, postId, otherUserId);
    });

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_POST_OWNER);
  }


  @Test
  @DisplayName("게시물 삭제 - 성공")
  void deletePost_Success() {
    // given
    PostAttachment attachment = PostAttachment.builder().savedFilename("file_to_delete.txt").build();

    // Mocking
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));
    when(postAttachmentRepository.findAllByPostPostId(postId)).thenReturn(List.of(attachment));

    // when
    postService.deletePost(postId, userId);

    // then
    verify(postAttachmentRepository).deleteAllByPostPostId(postId);
    verify(fileUploadService).delete("file_to_delete.txt");
    verify(commentRepository).deleteAllByPostPostId(postId);
    verify(postLikeRepository).deleteAllByPostPostId(postId);
    verify(postBookmarkRepository).deleteAllByPostPostId(postId);
    verify(postRepository).delete(mockPost);
  }

  @Test
  @DisplayName("게시물 목록 조회 - 성공")
  void getPosts_Success() {
    // given
    int page = 0;
    int size = 10;
    Pageable pageable = PageRequest.of(page, size, Sort.by(Direction.DESC, "createdDate"));
    List<Post> postList = List.of(mockPost);
    Page<Post> postPage = new PageImpl<>(postList, pageable, postList.size());

    // Mocking
    when(boardRepository.findById(boardId)).thenReturn(Optional.of(mockBoard));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser)); // userId 조회 추가
    when(postRepository.findAllByBoard(mockBoard, pageable)).thenReturn(postPage);

    // 좋아요/북마크 Mocking (mapToPostResponse에서 호출됨)
    when(postLikeRepository.existsByUserUserIdAndPostPostId(userId, postId)).thenReturn(true);
    when(postBookmarkRepository.existsByUserUserIdAndPostPostId(userId, postId)).thenReturn(false);

    // when
    Page<PostResponse> result = postService.getPosts(boardId, userId, page, size);

    // then
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().get(0).getPostId()).isEqualTo(postId);
    // DTO 변환 확인
    assertThat(result.getContent().get(0).getUser().getName()).isEqualTo(mockUser.getName());
    assertThat(result.getContent().get(0).getBoard().getBoardName()).isEqualTo(mockBoard.getBoardName());
    // 좋아요/북마크 상태 확인
    assertThat(result.getContent().get(0).getIsLiked()).isTrue();
    assertThat(result.getContent().get(0).getIsBookmarked()).isFalse();
  }

  @Test
  @DisplayName("게시물 상세 조회 - 성공 (대댓글 포함)")
  void getPostDetail_Success_WithReplies() {
    // given
    int page = 0;
    int size = 10;
    Pageable commentPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdDate"));

    // 댓글 Mock 데이터 생성
    Comment parentComment = Comment.builder()
        .commentId(UUID.randomUUID())
        .post(mockPost)
        .user(mockUser)
        .content("부모댓글1")
        .parentComment(null)
        .build();

    Comment childComment = Comment.builder()
        .commentId(UUID.randomUUID())
        .post(mockPost)
        .user(mockUser)
        .content("대댓글1")
        .parentComment(parentComment)
        .build();

    Page<Comment> parentCommentPage = new PageImpl<>(List.of(parentComment), commentPageable, 1);

    // Mocking
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser)); // userId 조회 추가

    // 댓글 조회 Mocking
    when(commentRepository.findAllByPostPostIdAndParentCommentIsNull(postId, commentPageable))
        .thenReturn(parentCommentPage);
    // 2. 자식 댓글 조회 Mocking
    when(commentRepository.findByParentComment(parentComment)).thenReturn(List.of(childComment));

    // 첨부파일 조회 Mocking
    when(postAttachmentRepository.findAllByPostPostId(postId)).thenReturn(Collections.emptyList());

    // 좋아요/북마크 Mocking (공통 빌더에서 호출됨)
    when(postLikeRepository.existsByUserUserIdAndPostPostId(userId, postId)).thenReturn(true);
    when(postBookmarkRepository.existsByUserUserIdAndPostPostId(userId, postId)).thenReturn(true);

    // when
    PostResponse result = postService.getPostDetail(postId, userId, page, size);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getPostId()).isEqualTo(postId);
    assertThat(result.getIsLiked()).isTrue();
    assertThat(result.getIsBookmarked()).isTrue();

    // 댓글 검증
    Page<CommentResponse> commentPage = result.getComments();
    assertThat(commentPage.getTotalElements()).isEqualTo(1); // 부모 댓글 1개

    CommentResponse parentResponse = commentPage.getContent().get(0);
    assertThat(parentResponse.getContent()).isEqualTo("부모댓글1");

    // 대댓글 검증
    assertThat(parentResponse.getReplies()).isNotNull();
    assertThat(parentResponse.getReplies().size()).isEqualTo(1);
    assertThat(parentResponse.getReplies().get(0).getContent()).isEqualTo("대댓글1");

    // N+1 쿼리 호출 검증 (부모 댓글 수만큼 findByParentComment 호출)
    verify(commentRepository, times(1)).findByParentComment(parentComment);
  }

  @Test
  @DisplayName("게시판 생성 - 성공")
  void createBoard_Success() {
    // given
    BoardRequest request = BoardRequest.builder()
        .boardName("새 게시판")
        .parentBoardId(mockParentBoard.getBoardId())
        .build();

    // Mocking
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(boardRepository.findById(mockParentBoard.getBoardId())).thenReturn(Optional.of(mockParentBoard));

    // ArgumentCaptor
    ArgumentCaptor<Board> boardCaptor = ArgumentCaptor.forClass(Board.class);

    // when
    postService.createBoard(request, userId);

    // then
    verify(boardRepository).save(boardCaptor.capture());
    Board savedBoard = boardCaptor.getValue();

    assertThat(savedBoard.getBoardName()).isEqualTo("새 게시판");
    assertThat(savedBoard.getCreatedBy()).isEqualTo(mockUser);
    assertThat(savedBoard.getParentBoard().getBoardId()).isEqualTo(mockParentBoard.getBoardId());
  }
}