package org.sejongisc.backend.point.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.point.dto.PointHistoryResponse;
import org.sejongisc.backend.point.entity.PointHistory;
import org.sejongisc.backend.point.entity.PointOrigin;
import org.sejongisc.backend.point.entity.PointReason;
import org.sejongisc.backend.point.repository.PointHistoryRepository;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PointHistoryServiceTest {

  @Mock
  private PointHistoryRepository pointHistoryRepository;

  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private PointHistoryService pointHistoryService;

  private UUID userId;
  private UUID originId;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    userId = UUID.randomUUID();
    originId = UUID.randomUUID();
  }

  @Test
  void 포인트리더보드_성공() {
    // given
    int period = 7; // 주간
    User u1 = User.builder()
        .userId(UUID.randomUUID())
        .name("a")
        .email("a@test.com")
        .point(300)
        .build();
    User u2 = User.builder()
        .userId(UUID.randomUUID())
        .name("b")
        .email("b@test.com")
        .point(100)
        .build();

    when(userRepository.findAllByOrderByPointDesc())
        .thenReturn(List.of(u1, u2));

    // when
    PointHistoryResponse response = pointHistoryService.getPointLeaderboard(period);

    // then
    assertThat(response.getLeaderboardUsers()).hasSize(2);
    assertThat(response.getLeaderboardUsers().get(0).getPoint()).isEqualTo(300);
    assertThat(response.getLeaderboardUsers().get(1).getPoint()).isEqualTo(100);
  }

  @Test
  void 포인트리더보드_실패_잘못된_period_예외발생() {
    assertThatThrownBy(() ->
        pointHistoryService.getPointLeaderboard(5) // 지원되지 않는 period
    )
        .isInstanceOf(CustomException.class)
        .hasMessage(ErrorCode.INVALID_PERIOD.getMessage());
  }

  @Test
  void 포인트기록_생성_성공_출석체크_적립() {
    // given
    int amount = 50;
    when(pointHistoryRepository.getCurrentBalance(userId)).thenReturn(100);

    PointHistory history = PointHistory.of(userId, amount, PointReason.ATTENDANCE, PointOrigin.ATTENDANCE, originId);
    when(pointHistoryRepository.save(any(PointHistory.class))).thenReturn(history);

    // when
    PointHistory result = pointHistoryService.createPointHistory(
        userId, amount, PointReason.ATTENDANCE, PointOrigin.ATTENDANCE, originId
    );

    // then
    assertThat(result.getAmount()).isEqualTo(50);
    assertThat(result.getReason()).isEqualTo(PointReason.ATTENDANCE);
    assertThat(result.getPointOrigin()).isEqualTo(PointOrigin.ATTENDANCE);
    verify(pointHistoryRepository).save(any(PointHistory.class));
  }

  @Test
  void 포인트기록_생성_실패_amount가_0이면_예외발생() {
    assertThatThrownBy(() ->
        pointHistoryService.createPointHistory(userId, 0, PointReason.ETC, PointOrigin.ADMIN, originId)
    )
        .isInstanceOf(CustomException.class)
        .hasMessage(ErrorCode.INVALID_POINT_AMOUNT.getMessage());
  }

  @Test
  void 포인트기록_생성_실패_잔액부족으로_예외발생() {
    // given
    when(pointHistoryRepository.getCurrentBalance(userId)).thenReturn(99);

    // when & then
    assertThatThrownBy(() ->
        pointHistoryService.createPointHistory(userId, -100, PointReason.BETTING, PointOrigin.BETTING, originId)
    )
        .isInstanceOf(CustomException.class)
        .hasMessage(ErrorCode.NOT_ENOUGH_POINT_BALANCE.getMessage());
  }

  @Test
  void 포인트기록_페이징조회_성공() {
    PageRequest pageRequest = PageRequest.of(0, 10);

    PointHistory h1 = PointHistory.of(userId, 100, PointReason.REGISTRATION, PointOrigin.REGISTRATION, originId);
    PointHistory h2 = PointHistory.of(userId, -50, PointReason.BETTING, PointOrigin.BETTING, originId);

    when(pointHistoryRepository.findAllByUserId(userId, pageRequest))
        .thenReturn(new PageImpl<>(List.of(h1, h2)));

    Page<PointHistory> result = pointHistoryService.getPointHistoryListByUserId(userId, pageRequest).getPointHistoryPage();

    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent().get(0).getReason()).isEqualTo(PointReason.REGISTRATION);
    assertThat(result.getContent().get(1).getReason()).isEqualTo(PointReason.BETTING);
  }

  @Test
  void 유저탈퇴시_포인트기록_삭제성공() {
    pointHistoryService.deleteAllPointHistoryByUserId(userId);

    verify(pointHistoryRepository).deleteAllByUserId(userId);
  }
}