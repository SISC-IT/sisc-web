package org.sejongisc.backend.board.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.dao.PostAttachmentRepository;
import org.sejongisc.backend.board.dao.PostRepository;
import org.sejongisc.backend.board.dto.PostRequest;
import org.sejongisc.backend.board.dto.PostResponse;
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
    private final PostAttachmentRepository postAttachmentRepository;
    private final UserRepository userRepository;

    public PostResponse createPost(PostRequest request, UUID userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Board board = Board.builder().id(request.getBoardId()).build();

        Post post = Post.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .author(author)
                .board(board)
                .build();

        if (request.getAttachments() != null) {
            for (PostRequest.AttachmentDto dto : request.getAttachments()) {
                PostAttachment attachment = PostAttachment.builder()
                        .filename(dto.getFilename())
                        .url(dto.getUrl())
                        .mimeType(dto.getMimeType())
                        .post(post)
                        .build();
                post.getAttachments().add(attachment);
            }
        }

        Post saved = postRepository.save(post);
        return toResponse(saved);
    }

    public PostResponse updatePost(UUID postId, PostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());

        post.getAttachments().clear();
        if (request.getAttachments() != null) {
            for (PostRequest.AttachmentDto dto : request.getAttachments()) {
                PostAttachment attachment = PostAttachment.builder()
                        .filename(dto.getFilename())
                        .url(dto.getUrl())
                        .mimeType(dto.getMimeType())
                        .post(post)
                        .build();
                post.getAttachments().add(attachment);
            }
        }

        return toResponse(post);
    }

    public void deletePost(UUID postId) {
        postRepository.deleteById(postId);
    }

    public List<PostResponse> searchPosts(String keyword) {
        return postRepository.findByTitleContainingOrContentContaining(keyword, keyword)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getAuthor().getName(),
                post.getAttachments().stream()
                        .map(a -> new PostResponse.AttachmentResponse(
                                a.getId(),
                                a.getFilename(),
                                a.getUrl()
                        ))
                        .collect(Collectors.toList()),
                post.getCreatedDate()
        );
    }
}
