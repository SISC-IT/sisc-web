package org.sejongisc.backend.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.board.dto.PostRequest;
import org.sejongisc.backend.board.dto.PostResponse;
import org.sejongisc.backend.board.service.PostService;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.user.entity.User;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    @Mock
    PostService postService;

    @InjectMocks
    PostController postController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(postController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("POST /api/posts - 게시물 생성 성공")
    void createPost_success() throws Exception {
        // 요청 객체
        PostRequest req = new PostRequest();
        req.setTitle("공지사항");
        req.setContent("이번 달 모임 일정 안내");

        // 응답 객체
        UUID postId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        PostResponse resp = new PostResponse(
                postId, "공지사항", "이번 달 모임 일정 안내", "관리자", List.of(), now
        );

        // 서비스 레이어 mock
        when(postService.createPost(any(PostRequest.class), any(UUID.class))).thenReturn(resp);

        // CustomUserDetails를 위한 mock User
        User mockUser = User.builder()
                .userId(UUID.randomUUID())
                .name("관리자")
                .email("admin@example.com")
                .password("encoded-password")
                .role("ROLE_ADMIN")
                .createdAt(LocalDateTime.now())
                .build();

        CustomUserDetails principal = new CustomUserDetails(mockUser);

        mockMvc.perform(post("/api/posts")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.title", is("공지사항")))
                .andExpect(jsonPath("$.authorName", is("관리자")));
    }

    @Test
    @DisplayName("PATCH /api/posts/{id} - 게시물 수정 성공")
    void updatePost_success() throws Exception {
        UUID postId = UUID.randomUUID();
        PostRequest req = new PostRequest();
        req.setTitle("수정된 제목");
        req.setContent("수정된 내용");

        PostResponse resp = new PostResponse(
                postId, "수정된 제목", "수정된 내용", "관리자", List.of(), LocalDateTime.now()
        );

        when(postService.updatePost(any(UUID.class), any(PostRequest.class))).thenReturn(resp);

        mockMvc.perform(patch("/api/posts/" + postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("수정된 제목")))
                .andExpect(jsonPath("$.content", is("수정된 내용")));
    }

    @Test
    @DisplayName("DELETE /api/posts/{id} - 게시물 삭제 성공")
    void deletePost_success() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(delete("/api/posts/" + postId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/posts/search - 게시물 검색 성공")
    void searchPosts_success() throws Exception {
        UUID postId = UUID.randomUUID();
        PostResponse resp = new PostResponse(
                postId, "검색된 글", "검색 본문", "홍길동", List.of(), LocalDateTime.now()
        );

        when(postService.searchPosts("검색")).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/posts/search").param("keyword", "검색"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title", is("검색된 글")))
                .andExpect(jsonPath("$[0].authorName", is("홍길동")));
    }
}
