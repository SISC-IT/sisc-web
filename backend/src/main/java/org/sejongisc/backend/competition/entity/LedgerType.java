package org.sejongisc.backend.competition.entity;

public enum LedgerType {
    DEPOSIT,  // 현금 → 예금
    WITHDRAW, // 예금 → 현금
    INTEREST  // 예금 이자
}
