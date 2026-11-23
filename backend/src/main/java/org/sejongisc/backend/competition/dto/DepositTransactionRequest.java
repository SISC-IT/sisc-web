package org.sejongisc.backend.competition.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DepositTransactionRequest(
        @NotNull(message = "입금·출금 구분은 필수입니다.")
        DepositTransactionType type,

        // 원 단위 정수만 받는다. 1.5나 "100" 같은 값은 400으로 거절한다.
        @NotNull(message = "금액은 필수입니다.")
        @Positive(message = "금액은 1원 이상이어야 합니다.")
        @JsonDeserialize(using = IntegerOnlyLongDeserializer.class)
        Long amount
) {
}
