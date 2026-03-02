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
import org.springframework.data.domain.Slice;
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

  @Operation(summary = "내 출석 로그 조회")
  @GetMapping("/logs/attendance")
  public ResponseEntity<List<ActivityLog>> getAttendanceLogs(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(userService.getAttendanceActivityLog(customUserDetails.getUserId()));
  }

  @Operation(summary = "내 활동 로그 조회")
  @GetMapping("/logs/board")
  public ResponseEntity<List<ActivityLog>> getBoardLogs(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
    return ResponseEntity.ok(userService.getBoardActivityLog(customUserDetails.getUserId()));
  }

  @Operation(
      summary = "비밀번호 재설정 : 인증코드와 새 비밀번호를 입력받아, 비밀번호를 변경합니다.",
      description = """
        
        ## 인증(JWT): **불필요**
        
        
        ## 요청 파라미터
        - **`code`**: 이메일로 받은 인증코드
        - **`newPassword`**: 새 비밀번호
        
        ## 요청 바디 ( `PasswordResetSendRequest` )
        - **`email`**: 가입된 이메일
        - **`studentId`**: 가입된 학번
        
        ## 동작 설명
        - 이메일 + 학번으로 사용자를 다시 확인합니다.
        - Redis에 저장된 인증코드와 입력한 `code`를 비교합니다.
        - 인증코드가 일치하면 새 비밀번호를 정책 검증 후 암호화하여 저장합니다.
        - 사용한 인증코드는 Redis에서 삭제합니다. (1회성)

        ## 반환값
        - 성공 메시지 (`비밀번호가 변경되었습니다.`)
        """)
  @PostMapping("/password/reset/confirm")
  public ResponseEntity<?> confirmReset(@RequestBody @Valid PasswordResetConfirmRequest req){
    userService.resetPasswordByCode(req);
    return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다."));
  }
}