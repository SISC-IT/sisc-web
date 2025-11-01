package org.sejongisc.backend.board.controller;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.domain.PostType;
import org.sejongisc.backend.board.dto.*;
import org.sejongisc.backend.board.service.PostService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    // 게시글 생성
    @PostMapping
    public UUID createPost(@RequestBody PostCreateRequest request,
                           @RequestParam UUID userId) {
        return postService.createPost(request, userId);
    }

    // 게시글 수정
    @PutMapping("/{postId}")
    public void updatePost(@PathVariable UUID postId,
                           @RequestBody PostUpdateRequest request) {
        postService.updatePost(postId, request);
    }

    // 게시글 삭제
    @DeleteMapping("/{postId}")
    public void deletePost(@PathVariable UUID postId) {
        postService.deletePost(postId);
    }

    // 게시글 조회 (공지/일반)
    @GetMapping
    public List<PostSummaryResponse> getPosts(@RequestParam UUID boardId,
                                              @RequestParam(required = false) PostType type) {
        return postService.getPosts(boardId, type);
    }

    // 게시글 검색
    @GetMapping("/search")
    public List<PostSummaryResponse> searchPosts(@RequestParam String keyword) {
        return postService.searchPosts(keyword);
    }

    // 댓글 생성
    @PostMapping("/{postId}/comments")
    public UUID createComment(@PathVariable UUID postId,
                              @RequestBody CommentCreateRequest request,
                              @RequestParam UUID userId) {
        return postService.createComment(request, userId);
    }

    // 댓글 수정
    @PutMapping("/comments/{commentId}")
    public void updateComment(@PathVariable UUID commentId,
                              @RequestBody CommentUpdateRequest request,
                              @RequestParam UUID userId) {
        postService.updateComment(commentId, userId, request);
    }

    // 댓글 삭제
    @DeleteMapping("/comments/{commentId}")
    public void deleteComment(@PathVariable UUID commentId,
                              @RequestParam UUID userId,
                              @RequestParam boolean isAdmin) {
        postService.deleteComment(commentId, userId, isAdmin);
    }
}
