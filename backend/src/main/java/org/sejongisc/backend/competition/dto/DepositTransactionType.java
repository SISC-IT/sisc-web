package org.sejongisc.backend.competition.dto;

import org.sejongisc.backend.competition.entity.LedgerType;

public enum DepositTransactionType {
    DEPOSIT(LedgerType.DEPOSIT),
    WITHDRAW(LedgerType.WITHDRAW);

    private final LedgerType ledgerType;

    DepositTransactionType(LedgerType ledgerType) {
        this.ledgerType = ledgerType;
    }

    public LedgerType ledgerType() {
        return ledgerType;
    }
}
