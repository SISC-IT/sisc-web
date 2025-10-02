package org.sejongisc.backend.point.service;

import jakarta.persistence.OptimisticLockException;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
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
  private User u1;
  private User u2;
  private final int smallerPoint = 99;
  private final int biggerPoint = 300;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    userId = UUID.randomUUID();
    originId = UUID.randomUUID();

    u1 = User.builder()
        .userId(userId)
        .name("a")
        .email("a@test.com")
        .point(smallerPoint)
        .build();
    u2 = User.builder()
        .userId(UUID.randomUUID())
        .name("b")
        .email("b@test.com")
        .point(biggerPoint)
        .build();
  }

  @Test
  void 포인트리더보드_성공() {
    // given
    int period = 7; // 주간

    when(userRepository.findAllByOrderByPointDesc())
        .thenReturn(List.of(u2, u1));

    // when
    PointHistoryResponse response = pointHistoryService.getPointLeaderboard(period);

    // then
    assertThat(response.getLeaderboardUsers()).hasSize(2);
    assertThat(response.getLeaderboardUsers().get(0).getPoint()).isEqualTo(biggerPoint);
    assertThat(response.getLeaderboardUsers().get(1).getPoint()).isEqualTo(smallerPoint);
  }

  @Test
  void 리더보드_포인트순_정렬확인() {
    when(userRepository.findAllByOrderByPointDesc()).thenReturn(List.of(u2, u1));

    PointHistoryResponse response = pointHistoryService.getPointLeaderboard(7);

    List<User> users = response.getLeaderboardUsers();
    assertThat(users).extracting(User::getPoint).containsExactly(biggerPoint, smallerPoint);
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
    when(userRepository.findById(userId)).thenReturn(Optional.of(u1));

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
  void 포인트기록_생성_낙관적락_재시도_후_성공() {
    // given
    int amount = 10;
    when(userRepository.findById(userId)).thenReturn(Optional.of(u1));

    PointHistory history = PointHistory.of(userId, amount, PointReason.ATTENDANCE, PointOrigin.ATTENDANCE, originId);

    // 첫 번째 save 호출은 락 충돌 예외 던지고, 두 번째 호출은 정상 반환
    when(pointHistoryRepository.save(any(PointHistory.class)))
        .thenThrow(new OptimisticLockException("동시성 충돌"))
        .thenReturn(history);

    // when
    PointHistory result = pointHistoryService.createPointHistory(
        userId, amount, PointReason.ATTENDANCE, PointOrigin.ATTENDANCE, originId
    );

    // then
    assertThat(result).isNotNull();
    assertThat(result.getAmount()).isEqualTo(10);

    // save가 최소 2번 호출되었는지 확인 (재시도 확인)
    verify(pointHistoryRepository, times(2)).save(any(PointHistory.class));
  }

  @Test
  void 포인트기록_생성_낙관적락_3회_실패시_예외발생() {
    // given
    int amount = 10;
    when(userRepository.findById(userId)).thenReturn(Optional.of(u1));

    when(pointHistoryRepository.save(any(PointHistory.class)))
        .thenThrow(new OptimisticLockException("동시성 충돌"))
        .thenThrow(new OptimisticLockException("동시성 충돌"))
        .thenThrow(new OptimisticLockException("동시성 충돌"));

    // when & then
    assertThatThrownBy(() ->
        pointHistoryService.createPointHistory(
            userId, amount, PointReason.ATTENDANCE, PointOrigin.ATTENDANCE, originId
        )
    )
        .isInstanceOf(CustomException.class)
        .hasMessage(ErrorCode.CONCURRENT_UPDATE.getMessage());

    verify(pointHistoryRepository, times(3)).save(any(PointHistory.class));
  }

  @Test
  void 포인트기록_생성_실패_유저없음_예외발생() {
    // given
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() ->
        pointHistoryService.createPointHistory(userId, 100, PointReason.REGISTRATION, PointOrigin.REGISTRATION, originId)
    )
        .isInstanceOf(CustomException.class)
        .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
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
    when(userRepository.findById(userId)).thenReturn(Optional.of(u1));

    // when & then
    assertThatThrownBy(() ->
        pointHistoryService.createPointHistory(userId, -100, PointReason.BETTING, PointOrigin.BETTING, originId)
    )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("잔액 부족으로 포인트를 차감할 수 없습니다.");
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