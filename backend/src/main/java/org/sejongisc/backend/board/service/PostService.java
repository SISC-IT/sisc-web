package org.sejongisc.backend.board.service;

import java.util.UUID;
import org.sejongisc.backend.board.dto.BoardRequest;
import org.sejongisc.backend.board.dto.PostRequest;
import org.sejongisc.backend.board.dto.PostResponse;
import org.springframework.data.domain.Page;

public interface PostService {

  // 게시물 작성
  void savePost(PostRequest request, UUID userId);

  // 게시물 수정
  void updatePost(PostRequest request, UUID postId, UUID userId);

  // 게시물 삭제
  void deletePost(UUID postId, UUID userId);

  // 게시물 조회
  Page<PostResponse> getPosts(UUID boardId, int pageNumber, int pageSize);

  // 게시물 검색
  Page<PostResponse> searchPosts(UUID boardId, String keyword, int pageNumber, int pageSize);

  // 게시물 상세 조회
  PostResponse getPostDetail(UUID postId, int pageNumber, int pageSize);

  // 게시판 생성
  void createBoard(BoardRequest request, UUID userId);
}
