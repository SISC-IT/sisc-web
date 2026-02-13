package org.sejongisc.backend.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.admin.dto.AdminUserRequest;
import org.sejongisc.backend.admin.dto.AdminUserResponse;
import org.sejongisc.backend.admin.dto.ExcelSyncResponse;
import org.sejongisc.backend.admin.service.AdminUserService;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.UserStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@Tag(name = "관리자 API", description = "운영진 및 개발자용 회원 관리 API")
public class AdminUserController {

    private final AdminUserService adminUserService;

    // --- [회장/운영진용] 회원 관리 API ---

    @Operation(
        summary = "엑셀 명단 업로드 및 동기화",
        description = """
        • .xlsx 형식만 업로드 가능합니다.\s
        • 학번 기준으로 회원을 생성하거나 기존 정보를 갱신합니다.\s
        • 신규 회원의 초기 비밀번호는 전화번호 숫자만(예: 01012345678)으로 설정됩니다.\s
        """
    )
    @PostMapping(value = "/upload-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PRESIDENT', 'SYSTEM_ADMIN')") // 회장, 개발자만 가능
    public ResponseEntity<ExcelSyncResponse> uploadMemberExcel(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(adminUserService.syncUsersFromExcel(file));
    }

    @Operation(
        summary = "전체 회원 목록 조회",
        description = """
        • 키워드, 기수, 권한(Role), 활동 상태(UserStatus) 조건으로 필터 조회합니다.\s
        • 키워드는 이름, 학번, 이메일 기준으로 검색됩니다.\s
        • 조건 미입력 시 전체 조회됩니다.
        """
    )
    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'MANAGER')") // TODO: 현재 MANAGER role은 존재하지 않음. 변경 필요
    public ResponseEntity<List<AdminUserResponse>> getAllUsers(@ModelAttribute AdminUserRequest request) {
        // TODO: 페이징 추후 고려
        return ResponseEntity.ok(adminUserService.findAllUsers(request));
    }

    @Operation(summary = "회원 활동 상태 변경", description = "ACTIVE, INACTIVE, GRADUATED 등으로 상태를 변경합니다.")
    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'MANAGER')")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable UUID userId,
            @RequestParam UserStatus status) {
        adminUserService.updateUserStatus(userId, status);
        return ResponseEntity.noContent().build();
    }

    // --- [시스템 관리자용 or 회장용] 권한 및 계정 제어 API ---
    // TODO : 회장 권한 논의 필요
    @Operation(summary = "회원 권한 변경", description = "특정 유저의 Role(PRESIDENT, VICE_PRESIDENT, TEAM_LEADER)을 변경합니다. (시스템 관리자용)")
    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> updateUserRole(
            @PathVariable UUID userId,
            @RequestParam Role role) {
        adminUserService.updateUserRole(userId, role);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "선배(SENIOR) 등급 변경", description = "특정 유저를 선배(SENIOR) 등급으로 변경합니다. (회장/관리자용)")
    @PatchMapping("/{userId}/senior")
    @PreAuthorize("hasAnyRole('PRESIDENT', 'SYSTEM_ADMIN')")
    public ResponseEntity<Void> promoteToSenior(@PathVariable UUID userId) {
        adminUserService.promoteToSenior(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "회원 강제 탈퇴", description = "시스템에서 유저를 완전히 삭제합니다. (시스템 관리자용)")
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> forceDeleteUser(@PathVariable UUID userId) {
        adminUserService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}