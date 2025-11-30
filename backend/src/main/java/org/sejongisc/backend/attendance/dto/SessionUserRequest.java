package org.sejongisc.backend.attendance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionUserRequest {

    @NotNull(message = "사용자 ID는 필수입니다")
    private UUID userId;
}
