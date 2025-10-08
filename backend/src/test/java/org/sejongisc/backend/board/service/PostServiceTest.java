package org.sejongisc.backend.board.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.board.dao.PostRepository;
import org.sejongisc.backend.board.dto.PostRequest;
import org.sejongisc.backend.board.dto.PostResponse;
import org.sejongisc.backend.board.entity.Board;
import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    PostRepository postRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    PostService postService;

    @Test
    @DisplayName("게시물 생성 성공")
    void createPost_success() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .userId(userId)
                .name("관리자")
                .build();

        PostRequest req = new PostRequest();
        req.setBoardId(UUID.randomUUID());
        req.setTitle("새 공지");
        req.setContent("내용");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0, Post.class);
            p.setId(UUID.randomUUID());
            p.setBoard(Board.builder().id(req.getBoardId()).build());
            p.setAuthor(user);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        // when
        PostResponse res = postService.createPost(req, userId);

        // then
        assertThat(res.getTitle()).isEqualTo("새 공지");
        assertThat(res.getAuthorName()).isEqualTo("관리자");
    }

    @Test
    @DisplayName("게시물 검색 성공")
    void searchPosts_success() {
        UUID postId = UUID.randomUUID();
        User user = User.builder()
                .userId(UUID.randomUUID())
                .name("홍길동")
                .build();

        Post post = Post.builder()
                .id(postId)
                .title("검색 테스트")
                .content("본문")
                .author(user)
                .board(Board.builder().id(UUID.randomUUID()).build())
                .createdAt(LocalDateTime.now())
                .build();

        when(postRepository.findByTitleContainingOrContentContaining("검색", "검색"))
                .thenReturn(List.of(post));

        List<PostResponse> results = postService.searchPosts("검색");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("검색 테스트");
        assertThat(results.get(0).getAuthorName()).isEqualTo("홍길동");
    }
}
