package org.sejongisc.backend.betting.entity;

public enum BetStatus {
    ACTIVE,
    DELETED, // 삭제
    CLOSED, // 정산 완료
    CANCELED // 취소
}