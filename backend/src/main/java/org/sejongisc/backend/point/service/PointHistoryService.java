package org.sejongisc.backend.point.service;

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
    // 포인트 증감 검증
    if (amount == 0) {
      throw new CustomException(ErrorCode.INVALID_POINT_AMOUNT);
    }

    User user = userRepository.findById(userId)
        .orElseThrow(()-> new CustomException(ErrorCode.USER_NOT_FOUND));
    int currentBalance = user.getPoint();

    // 포인트 차감 시 잔액 부족 검증
    if (amount < 0 && currentBalance + amount < 0) {
      throw new CustomException(ErrorCode.NOT_ENOUGH_POINT_BALANCE);
    }

    // 포인트 기록 생성 및 저장
    PointHistory pointHistory = pointHistoryRepository.save(
        PointHistory.of(userId, amount, reason, origin, originId)
    );

    user.updatePoint(amount);
    return pointHistory;
  }

  // 유저 탈퇴 시 특정 유저의 모든 포인트 기록 삭제
  public void deleteAllPointHistoryByUserId(UUID userId) {
    pointHistoryRepository.deleteAllByUserId(userId);
  }
}
