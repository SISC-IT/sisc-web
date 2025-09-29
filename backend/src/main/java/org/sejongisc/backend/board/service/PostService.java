package org.sejongisc.backend.board.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.dao.AttachmentRepository;
import org.sejongisc.backend.board.dao.PostRepository;
import org.sejongisc.backend.board.dto.PostRequest;
import org.sejongisc.backend.board.dto.PostResponse;
import org.sejongisc.backend.board.entity.Attachment;
import org.sejongisc.backend.board.entity.Post;
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
    private final AttachmentRepository attachmentRepository;

    // 게시물 생성
    public PostResponse createPost(PostRequest request, User author) {
        Post post = Post.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .postType(request.getPostType())
                .author(author)
                .build();

        Post saved = postRepository.save(post);

        if (request.getAttachments() != null) {
            for (PostRequest.AttachmentDto dto : request.getAttachments()) {
                Attachment attachment = Attachment.builder()
                        .fileName(dto.getFileName())
                        .fileUrl(dto.getFileUrl())
                        .post(saved)
                        .build();
                attachmentRepository.save(attachment);
                saved.getAttachments().add(attachment);
            }
        }

        return toResponse(saved);
    }

    // 게시물 수정
    public PostResponse updatePost(UUID postId, PostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setPostType(request.getPostType());

        // 첨부파일 갱신 (간단하게 전체 교체)
        post.getAttachments().clear();
        if (request.getAttachments() != null) {
            for (PostRequest.AttachmentDto dto : request.getAttachments()) {
                Attachment attachment = Attachment.builder()
                        .fileName(dto.getFileName())
                        .fileUrl(dto.getFileUrl())
                        .post(post)
                        .build();
                attachmentRepository.save(attachment);
                post.getAttachments().add(attachment);
            }
        }

        return toResponse(post);
    }

    // 게시물 삭제
    public void deletePost(UUID postId) {
        postRepository.deleteById(postId);
    }

    // 게시물 검색
    public List<PostResponse> searchPosts(String keyword) {
        return postRepository.findByTitleContainingOrContentContaining(keyword, keyword)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getPostId(),
                post.getTitle(),
                post.getContent(),
                post.getPostType(),
                post.getAuthor().getName(),
                post.getAttachments().stream()
                        .map(a -> new PostResponse.AttachmentResponse(
                                a.getFileId(),
                                a.getFileName(),
                                a.getFileUrl()
                        ))
                        .collect(Collectors.toList()),
                post.getCreatedDate()
        );
    }
}
