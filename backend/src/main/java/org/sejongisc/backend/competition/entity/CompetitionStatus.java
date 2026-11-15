package org.sejongisc.backend.competition.entity;

public enum CompetitionStatus {
    READY,      // 시작 전
    ACTIVE,     // 진행 중
    ENDED,      // 종료, 결과 계산 완료 (사용자에게 비공개)
    FINALIZED   // 운영진 확정, 결과 공개
}
