package org.sejongisc.backend.admin.dto;

import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.UserStatus;

/**
 * 관리자 페이지의 사용자 필터링/검색 조회 요청
 */
public record AdminUserRequest (
    String keyword, // 키워드
    Integer generation, // 기수
    Role role, // 권한
    UserStatus status // 상태
) {
}