package org.sejongisc.backend.point.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.point.dto.PointHistoryItem;
import org.sejongisc.backend.point.dto.PointHistoryResponse;
import org.sejongisc.backend.point.repository.LedgerEntryRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointHistoryService {
  private final LedgerEntryRepository ledgerEntryRepository;

  /**
   * 특정 유저의 포인트 기록 페이징 조회
   */
  @Transactional(readOnly = true)
  public PointHistoryResponse getPointHistory(UUID userId, Pageable pageable) {
    return new PointHistoryResponse(
      ledgerEntryRepository.findAllByOwnerId(userId, pageable)
        .map(PointHistoryItem::from)
    );
  }
}
