package org.sejongisc.backend.attendance.dto.sessionUser;

import java.util.UUID;
import org.sejongisc.backend.user.entity.User;

public record AvailableSessionUserResponse(
    UUID userId,
    String studentId,
    String name,
    String teamName
) {
  public static AvailableSessionUserResponse from(User user) {
    return new AvailableSessionUserResponse(
        user.getUserId(),
        user.getStudentId(),
        user.getName(),
        user.getTeamName()
    );
  }
}
