package org.sejongisc.backend.board.service;

import org.sejongisc.backend.board.dto.CommentRequest;
import org.sejongisc.backend.board.dto.PostRequest;
import org.sejongisc.backend.board.dto.PostResponse;
import org.sejongisc.backend.board.entity.BoardType;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface PostService {

  // 게시물 작성
  void savePost(PostRequest request, UUID userId);

  // 게시물 수정
  void updatePost(PostRequest request, UUID postId, UUID userId);

  // 게시물 삭제
  void deletePost(UUID postId, UUID userId);

  // 게시물 조회 (전체)
  Page<PostResponse> getPosts(BoardType boardType, int pageNumber, int pageSize);

  // 게시물 검색 (제목/내용)
  Page<PostResponse> searchPosts(String keyword, int pageNumber, int pageSize);

  // 게시물 상세 조회
  PostResponse getPostDetail(UUID postId, int pageNumber, int pageSize);

  // 댓글 작성
  void createComment(CommentRequest request, UUID userId);

  // 댓글 수정
  void updateComment(CommentRequest request, UUID commentId, UUID userId);

  // 댓글 삭제
  void deleteComment(UUID commentId, UUID userId);

  // 좋아요
  void toggleLike(UUID postId, UUID userId);

  // 북마크
  void toggleBookmark(UUID postId, UUID userId);
}
