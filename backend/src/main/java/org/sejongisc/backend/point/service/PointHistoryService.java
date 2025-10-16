package org.sejongisc.backend.point.service;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.point.dto.PointHistoryResponse;
import org.sejongisc.backend.point.entity.PointHistory;
import org.sejongisc.backend.point.entity.PointOrigin;
import org.sejongisc.backend.point.entity.PointReason;
import org.sejongisc.backend.point.repository.PointHistoryRepository;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 포인트 증감 기록 서비스
 * userId에 대한 검증 로직이 없습니다.
 * 따라서 customUserDetails.getUserId() 에서 가져오는 userId를 사용해야합니다.
 * 해당 userId는 필터에서 검증이 완료되기에, 검증할 필요가 없기 때문입니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PointHistoryService {

  private final PointHistoryRepository pointHistoryRepository;
  private final UserRepository userRepository;

  public PointHistoryResponse getPointLeaderboard(int period) {
    // period: 1(일간), 7(주간), 30(월간)
    if (period != 1 && period != 7 && period != 30) {
      throw new CustomException(ErrorCode.INVALID_PERIOD);
    }

    return PointHistoryResponse.builder()
        .leaderboardUsers(userRepository.findAllByOrderByPointDesc())
        .build();
  }

  // 특정 유저의 포인트 기록 페이징 조회 (포인트 기록은 많아질 수 있으므로 페이징 처리)
  public PointHistoryResponse getPointHistoryListByUserId(UUID userId, PageRequest pageRequest) {
    return PointHistoryResponse.builder()
        .pointHistoryPage(pointHistoryRepository.findAllByUserId(userId, pageRequest))
        .build();
  }

  // 포인트 증감 기록 생성 및 유저 포인트 업데이트
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Retryable(
      // 어떤 예외가 발생했을 때 재시도할지 지정합니다.
      include = {OptimisticLockingFailureException.class},
      // 최대 재시도 횟수를 지정합니다 - 최초 1회 + 재시도 2회 = 총 3회
      maxAttempts = 3,
      // 재시도 사이의 지연 시간을 설정합니다 - 100ms
      backoff = @Backoff(delay = 100)
  )
  public PointHistory createPointHistory(UUID userId, int amount, PointReason reason, PointOrigin origin, UUID originId) {
    if (amount == 0) {
      throw new CustomException(ErrorCode.INVALID_POINT_AMOUNT);
    }

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    if (user.getPoint() + amount < 0) {
      throw new CustomException(ErrorCode.NOT_ENOUGH_POINT_BALANCE);
    }

    log.info("포인트 업데이트 시도: userId={}, currentPoint={}, amount={}", userId, user.getPoint(), amount);
    user.updatePoint(amount);

    PointHistory history = PointHistory.of(userId, amount, reason, origin, originId);
    return pointHistoryRepository.save(history);
  }

  /**
   * @Retryable에서 모든 재시도를 실패했을 때 호출될 메서드입니다.
   * @param e @Retryable에서 발생한 마지막 예외
   * @param userId, ... 원본 메서드와 동일한 파라미터
   */
  @Recover
  public PointHistory recover(OptimisticLockingFailureException e, UUID userId, int amount, PointReason reason, PointOrigin origin, UUID originId) {
    log.error("포인트 업데이트 최종 실패: userId={}, amount={}", userId, amount, e);
    throw new CustomException(ErrorCode.CONCURRENT_UPDATE);
  }

  // 유저 탈퇴 시 특정 유저의 모든 포인트 기록 삭제
  @Transactional
  public void deleteAllPointHistoryByUserId(UUID userId) {
    pointHistoryRepository.deleteAllByUserId(userId);
  }
}
