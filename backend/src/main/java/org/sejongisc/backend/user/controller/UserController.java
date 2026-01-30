package org.sejongisc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.controller.AuthCookieHelper;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.common.auth.dto.SignupRequest;
import org.sejongisc.backend.common.auth.dto.SignupResponse;
import org.sejongisc.backend.common.auth.service.RefreshTokenService;
import org.sejongisc.backend.user.dto.*;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
@Slf4j
@Tag(name = "사용자 API", description = "회원 정보 조회 및 수정 관련 API")
public class UserController {

  private final UserService userService;
  private final RefreshTokenService refreshTokenService;
  private final AuthCookieHelper authCookieHelper;

  @Operation(summary = "회원 가입", description = "회장이 승인하기 전까지 PENDING 상태가 유지되며, 웹사이트를 사용할 수 없습니다.")
  @ApiResponse(responseCode = "201", description = "회원가입 성공")
  @PostMapping("/signup")
  public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(userService.signup(request));
  }

  @Operation(summary = "회원 탈퇴", description = "UserStatus.OUT 으로 변경하여 softDelete 처리 후, 리프레시 토큰을 삭제합니다.")
  @DeleteMapping("/withdraw")
  public ResponseEntity<Void> withdraw(@AuthenticationPrincipal CustomUserDetails user) {
    userService.deleteUserSoftDelete(user.getUserId());
    refreshTokenService.deleteByUserId(user.getUserId());

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

  /*
  @Operation(summary = "아이디 찾기")
  @PostMapping("/id/find")
  public ResponseEntity<?> findUserID(@RequestBody @Valid UserIdFindRequest request) {
    String email = userService.findEmailByNameAndPhone(request.name(), request.phoneNumber());
    return ResponseEntity.ok(Map.of("email", email));
  }
  */

  // TODO : 비밀번호 재설정 시 학번 입력 고려
  @Operation(summary = "비밀번호 재설정 : 이메일로 인증코드를 전송합니다.")
  @PostMapping("/password/reset/send")
  public ResponseEntity<?> sendReset(@RequestBody @Valid PasswordResetSendRequest req){
    userService.passwordReset(req.email().trim());
    return ResponseEntity.ok(Map.of("message", "인증코드를 전송했습니다."));
  }
}