package org.sejongisc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.activity.entity.ActivityLog;
import org.sejongisc.backend.common.auth.controller.AuthCookieHelper;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.common.auth.dto.SignupRequest;
import org.sejongisc.backend.common.auth.dto.SignupResponse;
import org.sejongisc.backend.common.auth.service.AuthService;
import org.sejongisc.backend.common.auth.service.RefreshTokenService;
import org.sejongisc.backend.user.dto.*;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
@Slf4j
@Tag(name = "03. 사용자 API", description = "회원 정보 조회 및 수정 관련 API")
public class UserController {

  private final UserService userService;
  private final AuthCookieHelper authCookieHelper;

  @Operation(summary = "회원 탈퇴", description = "UserStatus.OUT 으로 변경하여 softDelete 처리 후, 리프레시 토큰을 삭제합니다.")
  @DeleteMapping("/withdraw")
  public ResponseEntity<Void> withdraw(@AuthenticationPrincipal CustomUserDetails user) {
    userService.deleteUserSoftDelete(user.getUserId());
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, authCookieHelper.deleteCookie("refresh").toString())
        .build();
  }

  @Operation(summary = "내 정보 조회")
  @GetMapping("/details")
  public ResponseEntity<UserInfoResponse> getUserInfo(@AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(new UserInfoResponse(user.getUserId(), user.getName(), user.getEmail(), user.getPhoneNumber(), user.getPoint(), user.getRole().name(), user.getAuthorities()));
  }

  @Operation(summary = "내 정보 수정")
  @PatchMapping("/details")
  public ResponseEntity<Void> updateUser(@RequestBody @Valid UserUpdateRequest request,
                                      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    userService.updateUser(customUserDetails.getUserId(), request);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "내 출석 로그 조회", description = "?page=0&size=20 방식으로 페이지네이션 조회 (최신순)")
  @GetMapping("/logs/attendance")
  public ResponseEntity<Page<ActivityLog>> getAttendanceLogs(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(userService.getAttendanceActivityLog(customUserDetails.getUserId(), pageable));
  }

  @Operation(summary = "내 활동 로그 조회", description = "?page=0&size=20 방식으로 페이지네이션 조회 (최신순)")
  @GetMapping("/logs/board")
  public ResponseEntity<Page<ActivityLog>> getBoardLogs(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(userService.getBoardActivityLog(customUserDetails.getUserId(), pageable));
  }
}
