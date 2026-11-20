package org.sejongisc.backend.competition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.sejongisc.backend.competition.entity.TradeSide;

public record CompetitionTradeRequest(
        @NotBlank(message = "종목코드는 필수입니다.")
        String ticker,

        @NotNull(message = "매수·매도 구분은 필수입니다.")
        TradeSide side,

        @NotNull(message = "수량은 필수입니다.")
        @Positive(message = "수량은 1주 이상이어야 합니다.")
        Long quantity,

        @NotNull(message = "확인한 종가 ID는 필수입니다.")
        UUID closePriceId
) {
}
