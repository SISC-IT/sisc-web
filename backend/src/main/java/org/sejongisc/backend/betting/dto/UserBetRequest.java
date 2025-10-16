package org.sejongisc.backend.betting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.betting.entity.BetOption;

import java.util.UUID;

@Getter
@Builder @AllArgsConstructor @NoArgsConstructor
public class UserBetRequest {

    @NotNull(message = "라운드 ID는 필수입니다.")
    private UUID roundId;

    @NotNull(message = "베팅 옵션은 필수입니다.")
    private BetOption option;

    @JsonProperty("isFree")
    private boolean free;

    @Min(value = 10, message = "베팅 포인트는 10 이상이어야 합니다.")
    private Integer stakePoints;
}
