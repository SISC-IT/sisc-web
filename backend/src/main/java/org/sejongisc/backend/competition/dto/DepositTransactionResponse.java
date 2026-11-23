package org.sejongisc.backend.competition.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.sejongisc.backend.competition.entity.AccountLedger;
import org.sejongisc.backend.competition.entity.LedgerType;

public record DepositTransactionResponse(
        UUID ledgerId,
        LedgerType type,
        BigDecimal amount,
        BigDecimal cashBalance,
        BigDecimal depositBalance,
        LocalDate interestDate,
        LocalDateTime executedAt
) {

    public static DepositTransactionResponse of(AccountLedger ledger) {
        return new DepositTransactionResponse(
                ledger.getLedgerId(),
                ledger.getType(),
                ledger.getAmount(),
                ledger.getCashAfter(),
                ledger.getDepositAfter(),
                ledger.getInterestDate(),
                ledger.getExecutedAt()
        );
    }
}
