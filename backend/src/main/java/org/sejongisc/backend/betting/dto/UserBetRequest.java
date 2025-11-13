package org.sejongisc.backend.betting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.betting.entity.BetOption;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "유저 베팅 요청 DTO")
public class UserBetRequest {

    @Schema(description = "베팅할 라운드의 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "라운드 ID는 필수입니다.")
    private UUID roundId;

    @Schema(description = "베팅 옵션 (상승/하락)", example = "RISE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "베팅 옵션은 필수입니다.")
    private BetOption option;

    @Schema(description = "무료 베팅 여부", example = "true")
    @JsonProperty("isFree")
    private boolean free;

    @Schema(description = "베팅에 사용할 포인트 (무료 베팅 시 null 또는 0)", example = "100")
    private Integer stakePoints;

    @Schema(description = "포인트 유효성 검증용 필드 (내부 검증 전용, 클라이언트가 직접 전송할 필요 없음)", hidden = true)
    @AssertTrue(message = "베팅 시 포인트는 10 이상이어야 합니다.")
    public boolean isStakePointsValid() {
        return free || (stakePoints != null && stakePoints >= 10);
    }
}
