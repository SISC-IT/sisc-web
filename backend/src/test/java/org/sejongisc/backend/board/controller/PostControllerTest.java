package org.sejongisc.backend.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.board.domain.PostType;
import org.sejongisc.backend.board.dto.*;
import org.sejongisc.backend.board.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostController.class)
class PostControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private PostService postService;

    // 슬라이스 테스트 시 JPA 메타모델 경고 방지 (환경에 따라 필요)
    @MockBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("[POST] /api/posts — 생성 200 & UUID 반환")
    void createPost_ok() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        PostCreateRequest req = new PostCreateRequest(
                UUID.randomUUID(),
                "제목",
                "내용",
                PostType.NORMAL,
                List.of(new PostAttachmentDto("a.hwp","application/haansoft-hwp","/a.hwp"))
        );

        given(postService.createPost(any(PostCreateRequest.class), eq(userId))).willReturn(postId);

        mockMvc.perform(post("/api/posts")
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().string(postId.toString()));

        verify(postService).createPost(any(PostCreateRequest.class), eq(userId));
    }

    @Test
    @DisplayName("[PUT] /api/posts/{postId} — 수정 200")
    void updatePost_ok() throws Exception {
        UUID postId = UUID.randomUUID();

        String body = """
          {
            "title":"수정제목",
            "content":"수정내용",
            "postType":"NOTICE",
            "attachments":[{"filename":"n.pptx","mimeType":"application/vnd.openxmlformats-officedocument.presentationml.presentation","url":"/n.pptx"}]
          }
        """;

        mockMvc.perform(put("/api/posts/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(postService).updatePost(eq(postId), any(PostUpdateRequest.class));
    }

    @Test
    @DisplayName("[DELETE] /api/posts/{postId} — 삭제 200")
    void deletePost_ok() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(delete("/api/posts/{postId}", postId))
                .andExpect(status().isOk());

        verify(postService).deletePost(postId);
    }

    @Test
    @DisplayName("[GET] /api/posts — 목록(공지/일반 분리) 200 & JSON 반환")
    void getPosts_ok() throws Exception {
        UUID boardId = UUID.randomUUID();

        given(postService.getPosts(eq(boardId), eq(PostType.NOTICE)))
                .willReturn(List.of(new PostSummaryResponse(UUID.randomUUID(), "t", 0, 0, null)));

        mockMvc.perform(get("/api/posts")
                        .param("boardId", boardId.toString())
                        .param("type", "NOTICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("t"));

        verify(postService).getPosts(boardId, PostType.NOTICE);
    }

    @Test
    @DisplayName("[GET] /api/posts/search — 검색 200 & JSON 반환")
    void searchPosts_ok() throws Exception {
        given(postService.searchPosts("키워드"))
                .willReturn(List.of(new PostSummaryResponse(UUID.randomUUID(), "k", 0, 1, null)));

        mockMvc.perform(get("/api/posts/search")
                        .param("keyword", "키워드"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("k"));

        verify(postService).searchPosts("키워드");
    }

    @Test
    @DisplayName("[POST] /api/posts/{postId}/comments — 댓글 생성 200 & UUID 반환")
    void createComment_ok() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        String body = """
          {"postId":"%s","content":"댓글","parentId":null}
        """.formatted(postId);

        given(postService.createComment(any(CommentCreateRequest.class), eq(userId))).willReturn(commentId);

        mockMvc.perform(post("/api/posts/{postId}/comments", postId)
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(commentId.toString()));

        verify(postService).createComment(any(CommentCreateRequest.class), eq(userId));
    }

    @Test
    @DisplayName("[PUT] /api/posts/comments/{commentId} — 댓글 수정 200")
    void updateComment_ok() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String body = """
          {"content":"수정댓글"}
        """;

        mockMvc.perform(put("/api/posts/comments/{commentId}", commentId)
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(postService).updateComment(eq(commentId), eq(userId), any(CommentUpdateRequest.class));
    }

    @Test
    @DisplayName("[DELETE] /api/posts/comments/{commentId} — 댓글 삭제 200")
    void deleteComment_ok() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/posts/comments/{commentId}", commentId)
                        .param("userId", userId.toString())
                        .param("isAdmin", "true"))
                .andExpect(status().isOk());

        verify(postService).deleteComment(commentId, userId, true);
    }
}
