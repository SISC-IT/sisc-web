package org.sejongisc.backend.board.controller;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.dto.PostRequest;
import org.sejongisc.backend.board.dto.PostResponse;
import org.sejongisc.backend.board.service.PostService;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final UserRepository userRepository;

    // 게시물 생성
    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @RequestBody PostRequest request,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        User author = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        PostResponse response = postService.createPost(request, author);
        return ResponseEntity.ok(response);
    }

    // 게시물 수정
    @PatchMapping("/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable UUID postId,
            @RequestBody PostRequest request
    ) {
        PostResponse response = postService.updatePost(postId, request);
        return ResponseEntity.ok(response);
    }

    // 게시물 삭제
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID postId) {
        postService.deletePost(postId);
        return ResponseEntity.noContent().build();
    }

    // 게시물 검색
    @GetMapping("/search")
    public ResponseEntity<List<PostResponse>> searchPosts(@RequestParam String keyword) {
        List<PostResponse> responses = postService.searchPosts(keyword);
        return ResponseEntity.ok(responses);
    }
}
