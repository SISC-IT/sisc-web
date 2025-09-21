package org.sejongisc.backend.point.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.point.entity.PointHistory;
import org.sejongisc.backend.point.entity.PointOrigin;
import org.sejongisc.backend.point.entity.PointReason;
import org.sejongisc.backend.point.repository.PointHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointHistoryService {

  private final PointHistoryRepository pointHistoryRepository;

  // 유저의 포인트 기록 페이징 조회 - 포인트 기록이 많아질 수 있으므로 페이징 처리
  public Page<PointHistory> getPointHistoryListByUserId(UUID userId, PageRequest pageRequest) {
    return pointHistoryRepository.findAllByUserId(userId, pageRequest);
  }

  // 포인트 증감 기록 생성
  public PointHistory createPointHistory(UUID userId, int amount, PointReason reason, PointOrigin origin, UUID originId) {
    // 포인트 증감 검증
    if (amount == 0) {
      throw new CustomException(ErrorCode.INVALID_POINT_AMOUNT);
    }

    int currentBalance = getCurrentPointBalance(userId);

    // 포인트 차감 시 잔액 부족 검증
    if (amount < 0) {
      if (currentBalance + amount < 0) {
        throw new CustomException(ErrorCode.NOT_ENOUGH_POINT_BALANCE);
      }
    }

    // 포인트 기록 생성
    PointHistory pointHistory = PointHistory.builder()
        .userId(userId)
        .amount(amount)
        .reason(reason)
        .pointOrigin(origin)
        .pointOriginId(originId)
        .build();

    return pointHistoryRepository.save(pointHistory);
  }

  // 특정 유저의 현재 포인트 잔액 조회
  public int getCurrentPointBalance(UUID userId) {
    return pointHistoryRepository.getCurrentBalance(userId);
  }

  // 유저 탈퇴 시 특정 유저의 모든 포인트 기록 삭제
  public void deleteAllPointHistoryByUserId(UUID userId) {
    pointHistoryRepository.deleteAllByUserId(userId);
  }
}
