package org.sejongisc.backend.board.service;

import org.sejongisc.backend.board.domain.PostType;
import org.sejongisc.backend.board.dto.*;

import java.util.List;
import java.util.UUID;

public interface PostService {

    UUID createPost(PostCreateRequest request, UUID userId);

    void updatePost(UUID postId, PostUpdateRequest request);

    void deletePost(UUID postId);

    List<PostSummaryResponse> getPosts(UUID boardId, PostType type);

    List<PostSummaryResponse> searchPosts(String keyword);

    UUID createComment(CommentCreateRequest request, UUID userId);

    void updateComment(UUID commentId, UUID userId, CommentUpdateRequest request);

    void deleteComment(UUID commentId, UUID userId, boolean isAdmin);
}
