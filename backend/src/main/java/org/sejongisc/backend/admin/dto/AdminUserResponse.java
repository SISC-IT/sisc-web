package org.sejongisc.backend.admin.dto;

import lombok.Builder;
import org.sejongisc.backend.user.entity.*;

import java.util.UUID;

@Builder
public record AdminUserResponse(
    UUID id,
    String studentId,
    String name,
    String email,
    String phoneNumber,
    long point, // Account 엔티티의 balance 값
    Grade grade,
    Role role,
    UserStatus status,
    Integer generation,
    String college,
    String department,
    String teamName,
    String positionName
) {
}