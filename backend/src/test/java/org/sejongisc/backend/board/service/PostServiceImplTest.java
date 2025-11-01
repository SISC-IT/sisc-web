package org.sejongisc.backend.board.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.sejongisc.backend.board.domain.*;
import org.sejongisc.backend.board.dto.*;
import org.sejongisc.backend.board.repository.*;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class PostServiceImplTest {

    @Mock private BoardRepository boardRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private PostAttachmentRepository postAttachmentRepository;

    @InjectMocks private PostServiceImpl postService;

    private UUID boardId;
    private UUID postId;
    private UUID userId;
    private Board board;
    private Post post;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        boardId = UUID.randomUUID();
        postId = UUID.randomUUID();
        userId = UUID.randomUUID();

        board = Board.builder()
                .id(boardId)
                .name("테스트 게시판")
                .isPrivate(false)
                .build();

        post = Post.builder()
                .id(postId)
                .board(board)
                .title("제목")
                .content("내용")
                .postType(PostType.NORMAL)
                .build();
    }

    // ---------- helper ----------
    private static <T> void setField(T instance, String field, Object value) {
        try {
            Field f = instance.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(instance, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PostUpdateRequest mkPostUpdateReq(String title, String content, PostType type, List<PostAttachmentDto> atts) {
        PostUpdateRequest req = new PostUpdateRequest();
        setField(req, "title", title);
        setField(req, "content", content);
        setField(req, "postType", type);
        setField(req, "attachments", atts);
        return req;
    }

    private CommentUpdateRequest mkCommentUpdateReq(String content) {
        CommentUpdateRequest req = new CommentUpdateRequest();
        setField(req, "content", content);
        return req;
    }

    // =========================
    // 1) 게시물 생성
    // =========================
    @Test
    @DisplayName("게시물 생성 - 첨부 포함")
    void createPost_success() {
        PostCreateRequest req = new PostCreateRequest(
                boardId,
                "새 글",
                "본문",
                PostType.NOTICE,
                List.of(
                        new PostAttachmentDto("a.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","/files/a.xlsx"),
                        new PostAttachmentDto("b.jpg","image/jpeg","/files/b.jpg")
                )
        );

        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));
        given(postRepository.save(any(Post.class))).willAnswer(inv -> {
            Post p = inv.getArgument(0);
            return Post.builder()
                    .id(postId)
                    .board(p.getBoard())
                    .title(p.getTitle())
                    .content(p.getContent())
                    .postType(p.getPostType())
                    .build();
        });

        UUID savedId = postService.createPost(req, userId);

        assertThat(savedId).isEqualTo(postId);
        verify(boardRepository, times(1)).findById(boardId);
        verify(postRepository, times(1)).save(any(Post.class));
        verify(postAttachmentRepository, times(1)).saveAll(anyList());
    }

    // =========================
    // 2) 게시물 수정
    // =========================
    @Test
    @DisplayName("게시물 수정 - 첨부 전체 교체")
    void updatePost_replaceAttachments() {
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(postAttachmentRepository.findByPostId(postId)).willReturn(
                List.of(PostAttachment.builder().id(UUID.randomUUID()).postId(postId).filename("old.png").mimeType("image/png").url("/old.png").build())
        );

        PostUpdateRequest req = mkPostUpdateReq(
                "수정 제목", "수정 내용", PostType.NORMAL,
                List.of(new PostAttachmentDto("n.pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation","/n.pptx"))
        );

        assertThatCode(() -> postService.updatePost(postId, req)).doesNotThrowAnyException();
        verify(postAttachmentRepository, times(1)).deleteAll(anyList());
        verify(postAttachmentRepository, times(1)).saveAll(anyList());
    }

    // =========================
    // 3) 게시물 삭제
    // =========================
    @Test
    @DisplayName("게시물 삭제 - 댓글/첨부 정리 후 본문 삭제")
    void deletePost_success() {
        given(postAttachmentRepository.findByPostId(postId))
                .willReturn(List.of(PostAttachment.builder().id(UUID.randomUUID()).postId(postId).filename("f").mimeType("m").url("u").build()));
        given(commentRepository.findByPostId(postId))
                .willReturn(List.of(Comment.builder().id(UUID.randomUUID()).postId(postId).userId(userId).content("c").build()));

        postService.deletePost(postId);

        verify(postAttachmentRepository, times(1)).deleteAll(anyList());
        verify(commentRepository, times(1)).deleteAll(anyList());
        verify(postRepository, times(1)).deleteById(postId);
    }

    // =========================
    // 4) 목록 조회 + 8) 공지/일반 분리
    // =========================
    @Test
    @DisplayName("게시물 목록 - 전체/유형별")
    void getPosts_byType() {
        given(postRepository.findByBoardId(boardId)).willReturn(List.of(post));
        given(postRepository.findByBoardIdAndPostType(boardId, PostType.NORMAL)).willReturn(List.of(post));
        given(commentRepository.findByPostId(postId)).willReturn(Collections.emptyList());

        assertThat(postService.getPosts(boardId, null)).hasSize(1);
        assertThat(postService.getPosts(boardId, PostType.NORMAL)).hasSize(1);
    }

    // =========================
    // 4) 검색
    // =========================
    @Test
    @DisplayName("게시물 검색 - 제목/내용 키워드")
    void searchPosts_keyword() {
        given(postRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase("키워드","키워드"))
                .willReturn(List.of(post));
        given(commentRepository.findByPostId(postId)).willReturn(Collections.emptyList());

        List<PostSummaryResponse> result = postService.searchPosts("키워드");
        assertThat(result).hasSize(1);
    }

    // =========================
    // 5) 댓글 생성
    // =========================
    @Test
    @DisplayName("댓글 생성 - 정상")
    void createComment_success() {
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> {
            Comment c = inv.getArgument(0);
            setField(c, "id", UUID.randomUUID());
            return c;
        });

        CommentCreateRequest req = new CommentCreateRequest();
        setField(req, "postId", postId);
        setField(req, "content", "댓글입니다");
        setField(req, "parentId", null);

        UUID cid = postService.createComment(req, userId);

        assertThat(cid).isNotNull();
        verify(commentRepository, times(1)).save(any(Comment.class));
    }

    // =========================
    // 6) 댓글 수정
    // =========================
    @Test
    @DisplayName("댓글 수정 - 작성자만 가능")
    void updateComment_onlyAuthor() {
        UUID cid = UUID.randomUUID();
        Comment mine = Comment.builder().id(cid).postId(postId).userId(userId).content("old").build();
        given(commentRepository.findById(cid)).willReturn(Optional.of(mine));

        assertThatCode(() -> postService.updateComment(cid, userId, mkCommentUpdateReq("new")))
                .doesNotThrowAnyException();

        Comment others = Comment.builder().id(cid).postId(postId).userId(UUID.randomUUID()).content("old").build();
        given(commentRepository.findById(cid)).willReturn(Optional.of(others));

        assertThatThrownBy(() -> postService.updateComment(cid, userId, mkCommentUpdateReq("new")))
                .isInstanceOf(SecurityException.class);
    }

    // =========================
    // 7) 댓글 삭제
    // =========================
    @Test
    @DisplayName("댓글 삭제 - 작성자 또는 관리자")
    void deleteComment_authorOrAdmin() {
        UUID cid = UUID.randomUUID();
        Comment mine = Comment.builder().id(cid).postId(postId).userId(userId).content("c").build();

        given(commentRepository.findById(cid)).willReturn(Optional.of(mine));
        given(commentRepository.findByParentId(cid)).willReturn(Collections.emptyList());

        // 작성자
        assertThatCode(() -> postService.deleteComment(cid, userId, false)).doesNotThrowAnyException();
        verify(commentRepository, times(1)).delete(any(Comment.class));

        // 관리자
        given(commentRepository.findById(cid)).willReturn(Optional.of(mine));
        assertThatCode(() -> postService.deleteComment(cid, UUID.randomUUID(), true)).doesNotThrowAnyException();

        // 타인 & 비관리자
        given(commentRepository.findById(cid)).willReturn(Optional.of(mine));
        assertThatThrownBy(() -> postService.deleteComment(cid, UUID.randomUUID(), false))
                .isInstanceOf(SecurityException.class);
    }
}
