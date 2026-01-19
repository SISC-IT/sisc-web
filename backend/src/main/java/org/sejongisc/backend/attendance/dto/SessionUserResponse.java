package org.sejongisc.backend.attendance.dto;


import lombok.Builder;
import java.time.LocalDateTime;
import java.util.UUID;
import org.sejongisc.backend.attendance.entity.SessionRole;
import org.sejongisc.backend.attendance.entity.SessionUser;

@Builder
public record SessionUserResponse(
    UUID sessionUserId,
    UUID sessionId,
    UUID userId,
    String userName,
    SessionRole sessionRole,
    LocalDateTime createdAt
) {
    public static SessionUserResponse from(SessionUser su) {
        return SessionUserResponse.builder()
            .sessionUserId(su.getSessionUserId())
            .sessionId(su.getAttendanceSession().getAttendanceSessionId())
            .userId(su.getUser().getUserId())
            .userName(su.getUser().getName())
            .sessionRole(su.getSessionRole())
            .createdAt(su.getCreatedDate())
            .build();
    }
}
