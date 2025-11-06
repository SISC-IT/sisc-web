package org.sejongisc.backend.board.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.sejongisc.backend.board.dto.CommentRequest;
import org.sejongisc.backend.board.dto.PostRequest;
import org.sejongisc.backend.board.dto.PostResponse;
import org.sejongisc.backend.board.entity.*;
import org.sejongisc.backend.board.repository.*;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.domain.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

  @Mock UserRepository userRepository;
  @Mock PostRepository postRepository;
  @Mock CommentRepository commentRepository;
  @Mock PostLikeRepository postLikeRepository;
  @Mock PostBookmarkRepository postBookmarkRepository;
  @Mock PostAttachmentRepository postAttachmentRepository;
  @Mock FileUploadService fileUploadService;

  @InjectMocks
  PostServiceImpl postService;

  UUID userId;
  User user;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    user = User.builder().userId(userId).role(Role.TEAM_MEMBER).build();
  }

  private PostRequest samplePostRequestWithFiles() {
    MockMultipartFile f = new MockMultipartFile("files", "note.txt", "text/plain",
        "hello".getBytes(StandardCharsets.UTF_8));
    PostRequest req = new PostRequest();
    req.setBoardType(BoardType.GENERAL);
    req.setPostType(PostType.NORMAL);
    req.setTitle("제목");
    req.setContent("내용");
    req.setFiles(List.of(f));
    return req;
  }

  @Test
  @DisplayName("게시글 저장 - 첨부파일 저장까지")
  void savePost_withFiles() {
    PostRequest req = samplePostRequestWithFiles();

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    UUID postId = UUID.randomUUID();
    Answer<Post> saveAnswer = inv -> {
      Post p = inv.getArgument(0);
      return Post.builder()
          .postId(postId)
          .user(p.getUser())
          .boardType(p.getBoardType())
          .title(p.getTitle())
          .content(p.getContent())
          .postType(p.getPostType())
          .build();
    };
    when(postRepository.save(any(Post.class))).thenAnswer(saveAnswer);

    when(fileUploadService.store(any(MultipartFile.class))).thenReturn("stored-note.txt");
    when(fileUploadService.getRootLocation()).thenReturn(java.nio.file.Path.of("/data/upload"));

    postService.savePost(req, userId);

    verify(postRepository, times(1)).save(any(Post.class));
    verify(postAttachmentRepository, times(1)).save(argThat(att ->
        "stored-note.txt".equals(att.getSavedFilename())
        && "note.txt".equals(att.getOriginalFilename())
    ));
  }

  @Test
  @DisplayName("게시글 수정 - 기존 첨부 삭제 후 신규 저장")
  void updatePost_replaceFiles() {
    UUID postId = UUID.randomUUID();
    PostRequest req = samplePostRequestWithFiles();

    Post existing = Post.builder()
        .postId(postId).user(user)
        .title("old").content("old").postType(PostType.NORMAL)
        .boardType(BoardType.GENERAL).build();

    when(postRepository.findById(postId)).thenReturn(Optional.of(existing));
    when(fileUploadService.store(any(MultipartFile.class))).thenReturn("new.txt");
    when(fileUploadService.getRootLocation()).thenReturn(java.nio.file.Path.of("/data/upload"));
    when(postAttachmentRepository.findAllByPostId(postId))
        .thenReturn(List.of(PostAttachment.builder()
            .postId(postId).savedFilename("old.txt").build()));

    postService.updatePost(req, postId, userId);

    verify(fileUploadService).delete("old.txt");
    verify(postAttachmentRepository).deleteAllByPostId(postId);
    verify(postAttachmentRepository).save(argThat(a ->
        a.getPostId().equals(postId) && a.getSavedFilename().equals("new.txt")));
  }

  @Test
  @DisplayName("게시글 수정 - 소유자 아님 -> 예외")
  void updatePost_notOwner_throws() {
    UUID postId = UUID.randomUUID();
    User other = User.builder().userId(UUID.randomUUID()).build();
    Post existing = Post.builder().postId(postId).user(other).build();
    when(postRepository.findById(postId)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> postService.updatePost(new PostRequest(), postId, userId))
        .isInstanceOf(CustomException.class)
        .hasMessage("게시물 수정/삭제 권한이 없습니다.");
  }

  @Test
  @DisplayName("게시글 삭제 - 첨부/댓글/좋아요/북마크 삭제 포함")
  void deletePost_allRelated() {
    UUID postId = UUID.randomUUID();
    Post post = Post.builder().postId(postId).user(user).build();

    when(postRepository.findById(postId)).thenReturn(Optional.of(post));
    when(postAttachmentRepository.findAllByPostId(postId))
        .thenReturn(List.of(
            PostAttachment.builder().postId(postId).savedFilename("a.txt").build(),
            PostAttachment.builder().postId(postId).savedFilename("b.txt").build()
        ));

    postService.deletePost(postId, userId);

    verify(fileUploadService).delete("a.txt");
    verify(fileUploadService).delete("b.txt");
    verify(postAttachmentRepository).deleteAllByPostId(postId);
    verify(commentRepository).deleteAllByPostId(postId);
    verify(postLikeRepository).deleteAllByPostId(postId);
    verify(postBookmarkRepository).deleteAllByPostId(postId);
    verify(postRepository).delete(post);
  }

  @Test
  @DisplayName("좋아요 토글 - 새로 추가")
  void toggleLike_add() {
    UUID postId = UUID.randomUUID();
    Post post = Post.builder().postId(postId).user(user).likeCount(0).build();
    when(postRepository.findById(postId)).thenReturn(Optional.of(post));
    when(postLikeRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());

    postService.toggleLike(postId, userId);

    verify(postLikeRepository).save(argThat(l ->
        l.getPostId().equals(postId) && l.getUserId().equals(userId)));
    assertThat(post.getLikeCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("좋아요 토글 - 취소")
  void toggleLike_remove() {
    UUID postId = UUID.randomUUID();
    Post post = Post.builder().postId(postId).user(user).likeCount(2).build();
    when(postRepository.findById(postId)).thenReturn(Optional.of(post));
    PostLike like = PostLike.builder().postId(postId).userId(userId).build();
    when(postLikeRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.of(like));

    postService.toggleLike(postId, userId);

    verify(postLikeRepository).delete(like);
    assertThat(post.getLikeCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("북마크 토글 - 추가/취소")
  void toggleBookmark_add_and_remove() {
    UUID postId = UUID.randomUUID();
    Post post = Post.builder().postId(postId).user(user).bookmarkCount(0).build();
    when(postRepository.findById(postId)).thenReturn(Optional.of(post));

    when(postBookmarkRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());
    postService.toggleBookmark(postId, userId);
    verify(postBookmarkRepository).save(argThat(b ->
        b.getPostId().equals(postId) && b.getUserId().equals(userId)));
    assertThat(post.getBookmarkCount()).isEqualTo(1);

    reset(postBookmarkRepository);
    when(postBookmarkRepository.findByPostIdAndUserId(postId, userId))
        .thenReturn(Optional.of(PostBookmark.builder().postId(postId).userId(userId).build()));
    postService.toggleBookmark(postId, userId);
    verify(postBookmarkRepository).delete(any(PostBookmark.class));
    assertThat(post.getBookmarkCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("댓글 생성 - 댓글수 증가")
  void createComment_increaseCount() {
    UUID postId = UUID.randomUUID();
    Post post = Post.builder().postId(postId).user(user).commentCount(0).build();

    when(postRepository.findById(postId)).thenReturn(Optional.of(post));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    CommentRequest req = new CommentRequest();
    req.setPostId(postId);
    req.setContent("hi");

    postService.createComment(req, userId);

    verify(commentRepository).save(any(Comment.class));
    assertThat(post.getCommentCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("댓글 삭제 - 작성자 또는 관리자만 가능, 댓글수 감소")
  void deleteComment_ownerOrAdmin() {
    UUID commentId = UUID.randomUUID();
    UUID postId = UUID.randomUUID();
    Post post = Post.builder().postId(postId).user(user).commentCount(3).build();
    Comment comment = Comment.builder().commentId(commentId).postId(postId).user(user).content("c").build();

    when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(postRepository.findById(postId)).thenReturn(Optional.of(post));

    postService.deleteComment(commentId, userId);
    verify(commentRepository).delete(comment);
    assertThat(post.getCommentCount()).isEqualTo(2);

    reset(commentRepository, userRepository, postRepository);
    User admin = User.builder().userId(UUID.randomUUID()).role(Role.PRESIDENT).build();
    Comment others = Comment.builder().commentId(commentId).postId(postId)
        .user(User.builder().userId(UUID.randomUUID()).role(Role.TEAM_MEMBER).build())
        .content("c").build();

    when(commentRepository.findById(commentId)).thenReturn(Optional.of(others));
    when(userRepository.findById(admin.getUserId())).thenReturn(Optional.of(admin));
    when(postRepository.findById(postId)).thenReturn(Optional.of(post));

    post.setCommentCount(5);
    postService.deleteComment(commentId, admin.getUserId());
    verify(commentRepository).delete(others);
    assertThat(post.getCommentCount()).isEqualTo(4);
  }

  @Test
  @DisplayName("게시글 목록 조회 - 매핑 검사")
  void getPosts_mapping() {
    Post p = Post.builder()
        .postId(UUID.randomUUID())
        .user(user)
        .boardType(BoardType.GENERAL)
        .title("t")
        .content("c")
        .postType(PostType.NORMAL)
        .bookmarkCount(1)
        .likeCount(2)
        .commentCount(3)
        .build();

    when(postRepository.findAllByBoardType(eq(BoardType.GENERAL), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(p)));

    Page<PostResponse> page = postService.getPosts(BoardType.GENERAL, 0, 20);

    assertThat(page.getContent()).hasSize(1);
    PostResponse pr = page.getContent().get(0);
    assertThat(pr.getTitle()).isEqualTo("t");
    assertThat(pr.getLikeCount()).isEqualTo(2);
  }
}
