package org.sejongisc.backend.board.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.domain.*;
import org.sejongisc.backend.board.dto.*;
import org.sejongisc.backend.board.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PostServiceImpl implements PostService {

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostAttachmentRepository postAttachmentRepository;

    @Override
    public UUID createPost(PostCreateRequest request, UUID userId) {
        if (request == null) throw new IllegalArgumentException("요청이 존재하지 않습니다.");
        if (request.getBoardId() == null) throw new IllegalArgumentException("boardId 가 필요합니다.");
        if (isBlank(request.getTitle())) throw new IllegalArgumentException("제목이 필요합니다.");
        if (isBlank(request.getContent())) throw new IllegalArgumentException("내용이 필요합니다.");
        if (request.getPostType() == null) throw new IllegalArgumentException("postType 이 필요합니다.");

        Board board = boardRepository.findById(request.getBoardId())
                .orElseThrow(() -> new NoSuchElementException("게시판을 찾을 수 없습니다. id=" + request.getBoardId()));

        Post post = Post.builder()
                .board(board)
                .title(request.getTitle())
                .content(request.getContent())
                .postType(request.getPostType())
                .build();

        Post saved = postRepository.save(post);

        // 첨부 저장
        List<PostAttachmentDto> files = request.getAttachments();
        if (files != null && !files.isEmpty()) {
            List<PostAttachment> entities = files.stream()
                    .map(a -> PostAttachment.builder()
                            .postId(saved.getId())
                            .filename(a.getFilename())
                            .mimeType(a.getMimeType())
                            .url(a.getUrl())
                            .build())
                    .collect(Collectors.toList());
            postAttachmentRepository.saveAll(entities);
        }

        return saved.getId();
    }

    @Override
    public void updatePost(UUID postId, PostUpdateRequest request) {
        if (postId == null) throw new IllegalArgumentException("postId 가 필요합니다.");
        if (request == null) throw new IllegalArgumentException("요청이 존재하지 않습니다.");

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("게시글을 찾을 수 없습니다. id=" + postId));

        post.update(request.getTitle(), request.getContent(), request.getPostType());

        // 첨부 교체
        List<PostAttachment> existing = postAttachmentRepository.findByPostId(postId);
        if (!existing.isEmpty()) {
            postAttachmentRepository.deleteAll(existing);
        }

        List<PostAttachmentDto> newFiles = request.getAttachments();
        if (newFiles != null && !newFiles.isEmpty()) {
            List<PostAttachment> toSave = newFiles.stream()
                    .map(a -> PostAttachment.builder()
                            .postId(postId)
                            .filename(a.getFilename())
                            .mimeType(a.getMimeType())
                            .url(a.getUrl())
                            .build())
                    .collect(Collectors.toList());
            postAttachmentRepository.saveAll(toSave);
        }
    }

    @Override
    public void deletePost(UUID postId) {
        if (postId == null) throw new IllegalArgumentException("postId 가 필요합니다.");

        List<PostAttachment> attachments = postAttachmentRepository.findByPostId(postId);
        if (!attachments.isEmpty()) {
            postAttachmentRepository.deleteAll(attachments);
        }

        List<Comment> comments = commentRepository.findByPostId(postId);
        if (!comments.isEmpty()) {
            commentRepository.deleteAll(comments);
        }

        postRepository.deleteById(postId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<PostSummaryResponse> getPosts(UUID boardId, PostType type) {
        if (boardId == null) throw new IllegalArgumentException("boardId 가 필요합니다.");

        List<Post> posts = (type == null)
                ? postRepository.findByBoardId(boardId)
                : postRepository.findByBoardIdAndPostType(boardId, type);

        return posts.stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<PostSummaryResponse> searchPosts(String keyword) {
        if (isBlank(keyword)) {
            return Collections.emptyList();
        }

        // ✅ 수정된 repository 메서드 사용
        List<Post> posts = postRepository.searchByKeyword(keyword);

        return posts.stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    // ✅ 여기 핵심 수정
    @Override
    public UUID createComment(UUID postId, CommentCreateRequest request, UUID userId) {
        if (postId == null) throw new IllegalArgumentException("postId 가 필요합니다.");
        if (request == null) throw new IllegalArgumentException("요청이 존재하지 않습니다.");
        if (userId == null) throw new IllegalArgumentException("userId 가 필요합니다.");
        if (isBlank(request.getContent())) throw new IllegalArgumentException("댓글 내용이 비어 있습니다.");

        // 게시글 존재 확인 (path의 postId로만 확인)
        postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("게시글을 찾을 수 없습니다. id=" + postId));

        Comment comment = Comment.builder()
                .postId(postId)
                .userId(userId)
                .content(request.getContent())
                .parentId(request.getParentId())
                .build();

        return commentRepository.save(comment).getId();
    }

    @Override
    public void updateComment(UUID commentId, UUID userId, CommentUpdateRequest request) {
        if (commentId == null) throw new IllegalArgumentException("commentId 가 필요합니다.");
        if (userId == null) throw new IllegalArgumentException("userId 가 필요합니다.");
        if (request == null || isBlank(request.getContent()))
            throw new IllegalArgumentException("수정 내용이 비어 있습니다.");

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("댓글을 찾을 수 없습니다. id=" + commentId));

        if (!comment.getUserId().equals(userId)) {
            throw new SecurityException("본인 댓글만 수정할 수 있습니다.");
        }

        comment.setContent(request.getContent());
    }

    @Override
    public void deleteComment(UUID commentId, UUID userId, boolean isAdmin) {
        if (commentId == null) throw new IllegalArgumentException("commentId 가 필요합니다.");
        if (userId == null) throw new IllegalArgumentException("userId 가 필요합니다.");

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("댓글을 찾을 수 없습니다. id=" + commentId));

        if (!isAdmin && !comment.getUserId().equals(userId)) {
            throw new SecurityException("작성자 또는 관리자만 삭제할 수 있습니다.");
        }

        // 자식 댓글 먼저 삭제
        List<Comment> children = commentRepository.findByParentId(commentId);
        if (!children.isEmpty()) {
            commentRepository.deleteAll(children);
        }

        commentRepository.delete(comment);
    }

    private PostSummaryResponse toSummary(Post p) {
        int likeCount = 0; // like count repo 없음
        int commentCount = commentRepository.findByPostId(p.getId()).size();

        return new PostSummaryResponse(
                p.getId(),
                p.getTitle(),
                likeCount,
                commentCount,
                p.getCreatedAt() // 이제 Post에 createdAt 있음
        );
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
