package org.sejongisc.backend.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionUserResponse {

    private UUID sessionUserId;

    private UUID userId;

    private UUID sessionId;

    private String userName;

    private LocalDateTime createdAt;
}
