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
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
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
  @Transactional
  public PointHistory createPointHistory(UUID userId, int amount, PointReason reason, PointOrigin origin, UUID originId) {
    if (amount == 0) {
      throw new CustomException(ErrorCode.INVALID_POINT_AMOUNT);
    }

    int maxRetries = 3;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getPoint() + amount < 0) {
          throw new CustomException(ErrorCode.NOT_ENOUGH_POINT_BALANCE);
        }
        user.updatePoint(amount);

        PointHistory history = PointHistory.of(userId, amount, reason, origin, originId);
        return pointHistoryRepository.save(history);
      } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
        log.warn("낙관적 락 충돌 발생 : 재시도 {}회차", attempt);
        if (attempt == maxRetries) {
          throw new CustomException(ErrorCode.CONCURRENT_UPDATE);
        }
        try {
          Thread.sleep(100); // 백오프 - 직접 sleep
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
      }
    }
    return null;
  }

  // 유저 탈퇴 시 특정 유저의 모든 포인트 기록 삭제
  public void deleteAllPointHistoryByUserId(UUID userId) {
    pointHistoryRepository.deleteAllByUserId(userId);
  }
}
