package org.sejongisc.backend.board.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import org.sejongisc.backend.user.repository.UserRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;

@ExtendWith(MockitoExtension.class)
class PostInteractionServiceTest {

  @InjectMocks
  private PostInteractionService postInteractionService;

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

  // 테스트용 공유 객체
  private User mockUser;
  private User mockAdmin;
  private User mockOtherUser;
  private Post mockPost;
  private Comment mockParentComment;
  private Comment mockChildComment;

  private UUID userId;
  private UUID adminId;
  private UUID otherUserId;
  private UUID postId;
  private UUID parentCommentId;
  private UUID childCommentId;

  @BeforeEach
  void setUp() {
    // 사용자 UUID
    userId = UUID.randomUUID();
    adminId = UUID.randomUUID();
    otherUserId = UUID.randomUUID();

    // 엔티티 UUID
    postId = UUID.randomUUID();
    parentCommentId = UUID.randomUUID();
    childCommentId = UUID.randomUUID();

    // Mock 사용자 객체
    mockUser = User.builder().userId(userId).role(Role.TEAM_MEMBER).build();
    mockAdmin = User.builder().userId(adminId).role(Role.PRESIDENT).build();
    mockOtherUser = User.builder().userId(otherUserId).role(Role.TEAM_MEMBER).build();

    // Mock 엔티티 객체 (모든 카운트를 Integer 0으로 초기화)
    mockPost = Post.builder().postId(postId).likeCount(0).commentCount(0).bookmarkCount(0).build();

    mockParentComment = Comment.builder()
        .commentId(parentCommentId)
        .user(mockUser)
        .post(mockPost)
        .parentComment(null) // 부모 댓글임
        .build();

    mockChildComment = Comment.builder()
        .commentId(childCommentId)
        .user(mockOtherUser)
        .post(mockPost)
        .parentComment(mockParentComment) // 자식 댓글임
        .build();
  }

  @Test
  @DisplayName("댓글 작성 - 성공 (원댓글)")
  void createComment_Success_Parent() {
    // given
    CommentRequest request = new CommentRequest(postId, "새 댓글", null);
    mockPost.setCommentCount(5); // 초기 댓글 수 (Integer)

    // Mocking
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

    // when
    postInteractionService.createComment(request, userId);

    // then
    ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
    verify(commentRepository, times(1)).save(commentCaptor.capture());

    Comment savedComment = commentCaptor.getValue();
    assertThat(savedComment.getContent()).isEqualTo("새 댓글");
    assertThat(savedComment.getUser()).isEqualTo(mockUser);
    assertThat(savedComment.getParentComment()).isNull();

    // Count 검증 (Integer)
    assertThat(mockPost.getCommentCount()).isEqualTo(6);
  }

  @Test
  @DisplayName("댓글 작성 - 성공 (대댓글)")
  void createComment_Success_Child() {
    // given
    CommentRequest request = new CommentRequest(postId, "대댓글", parentCommentId);
    mockPost.setCommentCount(5); // (Integer)

    // Mocking
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(commentRepository.findById(parentCommentId)).thenReturn(Optional.of(mockParentComment));

    // when
    postInteractionService.createComment(request, userId);

    // then
    ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
    verify(commentRepository, times(1)).save(commentCaptor.capture());

    Comment savedComment = commentCaptor.getValue();
    assertThat(savedComment.getContent()).isEqualTo("대댓글");
    assertThat(savedComment.getParentComment()).isEqualTo(mockParentComment);
    assertThat(mockPost.getCommentCount()).isEqualTo(6);
  }

  @Test
  @DisplayName("댓글 작성 - 실패 (대대댓글 시도)")
  void createComment_Fail_ReplyToReply() {
    // given
    // mockChildComment는 parentComment를 부모로 가짐 (즉, 1-depth 대댓글임)
    CommentRequest request = new CommentRequest(postId, "대대댓글", childCommentId);

    // Mocking
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(commentRepository.findById(childCommentId)).thenReturn(Optional.of(mockChildComment));

    // when & then
    CustomException exception = assertThrows(CustomException.class, () -> {
      postInteractionService.createComment(request, userId);
    });

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_CHILD_COMMENT);
    verify(commentRepository, never()).save(any(Comment.class));
  }

  @Test
  @DisplayName("댓글 수정 - 성공")
  void updateComment_Success() {
    // given
    CommentRequest request = new CommentRequest(null, "수정된 내용", null);

    // Mocking
    when(commentRepository.findById(parentCommentId)).thenReturn(Optional.of(mockParentComment));

    // when
    postInteractionService.updateComment(request, parentCommentId, userId);

    // then
    assertThat(mockParentComment.getContent()).isEqualTo("수정된 내용");
  }

  @Test
  @DisplayName("댓글 수정 - 실패 (작성자 불일치)")
  void updateComment_Fail_InvalidOwner() {
    // given
    CommentRequest request = new CommentRequest(null, "수정 시도", null);

    // Mocking
    when(commentRepository.findById(parentCommentId)).thenReturn(Optional.of(mockParentComment));

    // when & then
    CustomException exception = assertThrows(CustomException.class, () -> {
      // mockParentComment의 작성자는 'userId'인데, 'otherUserId'로 수정 시도
      postInteractionService.updateComment(request, parentCommentId, otherUserId);
    });

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_COMMENT_OWNER);
  }

  @Test
  @DisplayName("댓글 삭제 - 성공 (작성자 본인, 자식 댓글 포함)")
  void deleteComment_Success_AsOwner_WithChildren() {
    // given
    mockPost.setCommentCount(3); // 부모 1 + 자식 1 + 기타 1

    // Mocking
    when(commentRepository.findById(parentCommentId)).thenReturn(Optional.of(mockParentComment));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));
    // 자식 댓글 1개 반환
    when(commentRepository.findByParentComment(mockParentComment)).thenReturn(List.of(mockChildComment));

    // when
    postInteractionService.deleteComment(parentCommentId, userId);

    // then
    verify(commentRepository).deleteAll(List.of(mockChildComment)); // 자식 일괄 삭제
    verify(commentRepository).delete(mockParentComment); // 부모 삭제

    // Count 검증 (3 - (1 + 1) = 1) (Integer)
    assertThat(mockPost.getCommentCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("댓글 삭제 - 성공 (관리자, 자식 댓글 없음)")
  void deleteComment_Success_AsAdmin() {
    // given
    mockPost.setCommentCount(1); // 부모 댓글 1개 (Integer)

    // Mocking
    when(commentRepository.findById(parentCommentId)).thenReturn(Optional.of(mockParentComment));
    when(userRepository.findById(adminId)).thenReturn(Optional.of(mockAdmin)); // 관리자(PRESIDENT)
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));
    when(commentRepository.findByParentComment(mockParentComment)).thenReturn(Collections.emptyList()); // 자식 없음

    // when
    // mockParentComment의 작성자는 'userId'이지만 'adminId'로 삭제 시도
    postInteractionService.deleteComment(parentCommentId, adminId);

    // then
    verify(commentRepository, never()).deleteAll(any()); // 자식 삭제 호출 안 됨
    verify(commentRepository).delete(mockParentComment); // 부모 삭제

    // Count 검증 (1 - 1 = 0) (Integer)
    assertThat(mockPost.getCommentCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("좋아요 토글 - 성공 (좋아요 추가)")
  void toggleLike_Success_AddLike() {
    // given
    mockPost.setLikeCount(5); // (Integer)

    // Mocking
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(postLikeRepository.findByPostPostIdAndUserUserId(postId, userId)).thenReturn(Optional.empty()); // 좋아요 없음

    // when
    postInteractionService.toggleLike(postId, userId);

    // then
    verify(postLikeRepository).save(any(PostLike.class));
    verify(postLikeRepository, never()).delete(any());
    assertThat(mockPost.getLikeCount()).isEqualTo(6); // (Integer)
  }

  @Test
  @DisplayName("좋아요 토글 - 성공 (좋아요 취소)")
  void toggleLike_Success_RemoveLike() {
    // given
    mockPost.setLikeCount(5); // (Integer)
    PostLike existingLike = PostLike.builder().post(mockPost).user(mockUser).build();

    // Mocking
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(postLikeRepository.findByPostPostIdAndUserUserId(postId, userId)).thenReturn(Optional.of(existingLike)); // 좋아요 있음

    // when
    postInteractionService.toggleLike(postId, userId);

    // then
    verify(postLikeRepository, never()).save(any(PostLike.class));
    verify(postLikeRepository).delete(existingLike);
    assertThat(mockPost.getLikeCount()).isEqualTo(4); // (Integer)
  }

  @Test
  @DisplayName("북마크 토글 - 성공 (북마크 추가)")
  void toggleBookmark_Success_AddBookmark() {
    // given
    mockPost.setBookmarkCount(5); // (Integer)

    // Mocking
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(postBookmarkRepository.findByPostPostIdAndUserUserId(postId, userId)).thenReturn(Optional.empty()); // 북마크 없음

    // when
    postInteractionService.toggleBookmark(postId, userId);

    // then
    verify(postBookmarkRepository).save(any(PostBookmark.class));
    verify(postBookmarkRepository, never()).delete(any());
    assertThat(mockPost.getBookmarkCount()).isEqualTo(6); // (Integer)
  }

  @Test
  @DisplayName("북마크 토글 - 성공 (북마크 취소)")
  void toggleBookmark_Success_RemoveBookmark() {
    // given
    mockPost.setBookmarkCount(5); // (Integer)
    PostBookmark existingBookmark = PostBookmark.builder().post(mockPost).user(mockUser).build();

    // Mocking
    when(postRepository.findById(postId)).thenReturn(Optional.of(mockPost));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(postBookmarkRepository.findByPostPostIdAndUserUserId(postId, userId)).thenReturn(Optional.of(existingBookmark)); // 북마크 있음

    // when
    postInteractionService.toggleBookmark(postId, userId);

    // then
    verify(postBookmarkRepository, never()).save(any(PostBookmark.class));
    verify(postBookmarkRepository).delete(existingBookmark);
    assertThat(mockPost.getBookmarkCount()).isEqualTo(4); // (Integer)
  }
}