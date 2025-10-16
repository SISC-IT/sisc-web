package org.sejongisc.backend.board.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.dao.PostRepository;
import org.sejongisc.backend.board.dto.PostRequest;
import org.sejongisc.backend.board.dto.PostResponse;
import org.sejongisc.backend.board.dto.PostAttachmentDto;
import org.sejongisc.backend.board.entity.Board;
import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.board.entity.PostAttachment;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public PostResponse createPost(PostRequest request, UUID userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("작성자 없음"));

        Post post = Post.builder()
                .board(Board.builder().id(request.getBoardId()).build())
                .author(author)
                .title(request.getTitle())
                .content(request.getContent())
                .postType(request.getPostType())
                .build();

        if (request.getAttachments() != null) {
            List<PostAttachment> atts = request.getAttachments().stream()
                    .map(dto -> PostAttachment.builder()
                            .fileName(dto.getFileName())
                            .fileUrl(dto.getFileUrl())
                            .post(post)
                            .build())
                    .toList();
            post.setAttachments(atts);
        }

        Post saved = postRepository.save(post);

        return new PostResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getContent(),
                saved.getAuthor().getName(),
                saved.getAttachments().stream()
                        .map(a -> {
                            PostAttachmentDto dto = new PostAttachmentDto();
                            dto.setFileName(a.getFileName());
                            dto.setFileUrl(a.getFileUrl());
                            return dto;
                        }).collect(Collectors.toList()),
                saved.getCreatedAt()
        );
    }

    public PostResponse updatePost(UUID postId, PostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물 없음"));

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());

        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getAuthor().getName(),
                post.getAttachments().stream()
                        .map(a -> {
                            PostAttachmentDto dto = new PostAttachmentDto();
                            dto.setFileName(a.getFileName());
                            dto.setFileUrl(a.getFileUrl());
                            return dto;
                        }).collect(Collectors.toList()),
                post.getUpdatedAt()
        );
    }

    public void deletePost(UUID postId) {
        postRepository.deleteById(postId);
    }

    public List<PostResponse> searchPosts(String keyword) {
        List<Post> posts = postRepository.findByTitleContainingOrContentContaining(keyword, keyword);
        return posts.stream()
                .map(p -> new PostResponse(
                        p.getId(),
                        p.getTitle(),
                        p.getContent(),
                        p.getAuthor().getName(),
                        p.getAttachments().stream().map(a -> {
                            PostAttachmentDto dto = new PostAttachmentDto();
                            dto.setFileName(a.getFileName());
                            dto.setFileUrl(a.getFileUrl());
                            return dto;
                        }).collect(Collectors.toList()),
                        p.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }
}
