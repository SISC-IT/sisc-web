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
    /**
     * User 엔티티와 포인트 잔액을 통해 DTO 생성
     */
    public static AdminUserResponse of(User user, long balance) {
        return AdminUserResponse.builder()
            .id(user.getUserId())
            .studentId(user.getStudentId())
            .name(user.getName())
            .email(user.getEmail())
            .phoneNumber(user.getPhoneNumber())
            .point(balance) // 복식부기 계정 잔액
            .grade(user.getGrade())
            .role(user.getRole())
            .status(user.getStatus())
            .generation(user.getGeneration())
            .college(user.getCollege())
            .department(user.getDepartment())
            .teamName(user.getTeamName())
            .positionName(user.getPositionName())
            .build();
    }
}