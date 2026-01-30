package org.sejongisc.backend.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.admin.dto.AdminUserRequest;
import org.sejongisc.backend.user.dto.UserInfoResponse; // 기존 DTO 활용
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.UserStatus;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Tag(name = "관리자 API", description = "운영진 및 개발자용 회원 관리 API")
public class AdminUserController {

    private final UserService userService;

    // --- [회장/운영진용] 회원 관리 API ---

    @Operation(summary = "전체 회원 목록 조회", description = "모든 회원의 정보를 조회합니다. (회장/관리자용)")
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'MANAGER')")
    public ResponseEntity<List<UserInfoResponse>> getAllUsers(@RequestBody AdminUserRequest request) {
        //return ResponseEntity.ok(userService.findAllUsers());             // TODO : 전체 조회, 기수별 조회, 이름 검색 등 기능 추가
        return null;
    }

    @Operation(summary = "회원 활동 상태 변경", description = "ACTIVE, INACTIVE, GRADUATED 등으로 상태를 변경합니다.")
    @PatchMapping("/users/{userId}/status")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'MANAGER')")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable UUID userId,
            @RequestParam UserStatus status) {
        //userService.updateUserStatus(userId, status);
        return ResponseEntity.ok(Map.of("message", "사용자 상태가 " + status + "(으)로 변경되었습니다."));
    }

    // --- [시스템 관리자용 or 회장용] 권한 및 계정 제어 API ---
    // TODO : 회장 권한 논의 필요
    @Operation(summary = "회원 권한 변경", description = "특정 유저의 Role(PRESIDENT, VICE_PRESIDENT, TEAM_LEADER)을 변경합니다.)")
    @PatchMapping("/users/{userId}/role")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> updateUserRole(
            @PathVariable UUID userId,
            @RequestParam Role role) {
        //userService.updateUserRole(userId, role);
        return ResponseEntity.ok(Map.of("message", "사용자 권한이 " + role + "(으)로 변경되었습니다."));
    }

    @Operation(summary = "회원 강제 탈퇴", description = "시스템에서 유저를 완전히 삭제합니다. (시스템 관리자용)")
    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> forceDeleteUser(@PathVariable UUID userId) {
        //userService.deleteUserWithOauth(userId);
        return ResponseEntity.ok(Map.of("message", "해당 사용자가 시스템에서 완전히 삭제되었습니다."));
    }
}