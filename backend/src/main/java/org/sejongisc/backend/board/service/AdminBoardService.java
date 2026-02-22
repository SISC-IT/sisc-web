package org.sejongisc.backend.board.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.board.dto.BoardRequest;
import org.sejongisc.backend.board.entity.Board;
import org.sejongisc.backend.board.repository.BoardRepository;
import org.sejongisc.backend.board.repository.PostRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBoardService {

  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final BoardRepository boardRepository;
  private final PostService postService;

  // 게시판 생성
  @Transactional
  public void createBoard(BoardRequest request, UUID userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    // 회장만 게시판 생성 가능
    if (!user.getRole().equals(Role.PRESIDENT)) {
      throw new CustomException(ErrorCode.BOARD_ACCESS_DENIED);
    }

    Board board;
    // 하위 게시판인 경우
    if (request.getParentBoardId() != null) {
      Board parentBoard = boardRepository.findById(request.getParentBoardId())
          .orElseThrow(() -> new CustomException(ErrorCode.BOARD_NOT_FOUND));

      board = Board.builder()
          .boardName(request.getBoardName())
          .createdBy(user)
          .parentBoard(parentBoard)
          .build();
    } else {
      // 상위 게시판인 경우
      board = Board.builder()
          .boardName(request.getBoardName())
          .createdBy(user)
          .parentBoard(null)
          .build();
    }

    boardRepository.save(board);
  }

  // 게시판 삭제
  @Transactional
  public void deleteBoard(UUID boardId, UUID boardUserId) {
    User user = userRepository.findById(boardUserId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    // 회장만 게시판 삭제 가능
    if (!user.getRole().equals(Role.PRESIDENT)) {
      throw new CustomException(ErrorCode.BOARD_ACCESS_DENIED);
    }

    // 상위 게시판이면 하위 게시판 목록을 조회
    List<UUID> targetBoardIds = Stream.concat(
        Stream.of(boardId), // 자신 포함
        boardRepository.findAllByParentBoard_BoardId(boardId).stream()
            .map(Board::getBoardId)
    ).toList();

    // 각 boardId마다 postId/userId 조회해서 삭제
    targetBoardIds.stream()
        .flatMap(id -> postRepository.findPostIdAndUserIdByBoardId(id).stream())
        .forEach(row -> postService.deletePost(row.getPostId(), row.getUserId()));
    targetBoardIds.forEach(boardRepository::deleteById);
  }
}
