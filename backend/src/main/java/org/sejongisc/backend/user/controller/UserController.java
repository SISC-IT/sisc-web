package org.sejongisc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.auth.dto.SignupRequest;
import org.sejongisc.backend.auth.dto.SignupResponse;
import org.sejongisc.backend.user.dto.*;
import org.sejongisc.backend.user.service.UserService;
import org.sejongisc.backend.user.service.projection.UserIdNameProjection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
@Slf4j
@Tag(name = "사용자 API", description = "회원 정보 조회 및 수정 관련 API")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내 정보 조회 API",
            description = "로그인된 사용자의 정보를 조회합니다. Access Token이 필요합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserInfoResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "userId": "9f6d0e22-45f1-4e5e-bc94-f1f6e7d28b44",
                                              "name": "홍길동",
                                              "email": "testuser@example.com",
                                              "phoneNumber": "01012345678",
                                              "point": 1500,
                                              "role": "USER",
                                              "authorities": ["ROLE_USER"]
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "인증되지 않은 사용자",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "인증이 필요합니다."
                                            }
                                            """))
                    )
            }
    )
    @GetMapping("/details")
    public ResponseEntity<?> getUserInfo(@AuthenticationPrincipal CustomUserDetails user) {
        if (user == null) {
            log.warn("인증되지 않은 사용자 접근 시도");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "인증이 필요합니다."));
        }

        log.info("email: {} 권한: {}", user.getUsername(), user.getAuthorities());

        UserInfoResponse response = new UserInfoResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getPoint(),
                user.getRole().name(),
                user.getAuthorities()
        );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "회원 정보 수정 API",
            description = "회원 정보를 수정합니다. 인증된 사용자만 이용 가능하며 본인 정보만 수정할 수 있습니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "수정 성공",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "회원 정보가 수정되었습니다."
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "인증되지 않은 사용자",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "인증 정보가 필요합니다."
                                            }
                                            """))
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "본인 이외의 정보 수정 시도",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "본인의 정보만 수정할 수 있습니다."
                                            }
                                            """))
                    )
            }
    )
    @PatchMapping("/{userId}")
    public ResponseEntity<?> updateUser(
            @PathVariable UUID userId,
            @RequestBody @Valid UserUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails authenticatedUser
    ) {
//        if(authenticatedUser == null){
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "인증 정보가 필요합니다."));
//        }

        log.info("인증된 사용자 ID={}, 요청한 userId={}", authenticatedUser.getUserId(), userId);

        // 본인 허용
        if (!authenticatedUser.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "본인의 정보만 수정할 수 있습니다."));
        }

        userService.updateUser(userId, request);
        return ResponseEntity.ok("회원 정보가 수정되었습니다.");
    }

    @Operation(
            summary = "아이디 찾기 API",
            description = """
                    사용자의 이름과 전화번호를 입력하면 가입된 이메일 주소를 반환합니다.
                    - 이름(name)과 전화번호(phoneNumber)가 모두 일치하는 회원만 조회됩니다.
                    - 일치하는 회원이 없을 경우 404 응답을 반환합니다.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "email": "testuser@example.com"
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "일치하는 회원 없음",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "해당 정보로 가입된 사용자를 찾을 수 없습니다."
                                            }
                                            """)
                            )
                    )
            }
    )
    @PostMapping("/id/find")
    public ResponseEntity<?> findUserID(@RequestBody @Valid UserIdFindRequest request) {
        String name = request.name();
        String phone = request.phoneNumber();
        String email = userService.findEmailByNameAndPhone(name, phone);

        if (email == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "해당 정보로 가입된 사용자를 찾을 수 없습니다."));
        }

        return ResponseEntity.ok(Map.of("email", email));
    }

    @Operation(
            summary = "비밀번호 재설정: 인증코드 발송 API",
            description = """
                    가입된 이메일 주소로 비밀번호 재설정을 위한 인증코드를 전송합니다.
                    - 인증코드는 3분간 유효합니다.
                    - 존재하지 않는 이메일일 경우 404 에러를 반환합니다.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "인증코드 발송 성공",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "인증코드를 전송했습니다."
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "이메일 미존재",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "해당 이메일로 가입된 사용자를 찾을 수 없습니다."
                                            }
                                            """)
                            )
                    )
            }
    )
    @PostMapping("/password/reset/send")
    public ResponseEntity<?> sendReset(@RequestBody @Valid PasswordResetSendRequest req){
        String email = req.email().trim();
        log.info("비밀번호 재설정 요청"); // 개인정보 로그 남기지 않기
        userService.passwordReset(email);
        return ResponseEntity.ok(Map.of("message", "인증코드를 전송했습니다."));
    }

    @Operation(
            summary = "비밀번호 재설정: 인증코드 검증 API",
            description = """
                    이메일과 인증코드를 검증하고, 유효한 경우 비밀번호 재설정용 토큰(`resetToken`)을 발급합니다.
                    - 인증코드는 3분간만 유효합니다.
                    - 검증에 성공하면 resetToken(10분 유효)을 반환합니다.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "검증 성공 및 resetToken 발급",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "resetToken": "c8a2434d-7e11-4f7e-a201-b9fbc9d7d43a"
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "잘못된 코드 또는 만료된 코드",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "인증코드가 올바르지 않거나 만료되었습니다."
                                            }
                                            """)
                            )
                    )
            }
    )
    @PostMapping("/password/reset/verify")
    public ResponseEntity<?> verifyReset(@RequestBody @Valid PasswordResetVerifyRequest req){
        String email = req.email().trim();
        String code = req.code().trim();

        String token = userService.verifyResetCodeAndIssueToken(email, code);
        return ResponseEntity.ok(Map.of("resetToken", token));
    }

    @Operation(
            summary = "비밀번호 재설정 최종 API",
            description = """
                검증된 resetToken과 새 비밀번호를 전달하면 비밀번호를 최종 변경합니다.
                - resetToken은 10분간 유효합니다.
                - 비밀번호 정책:
                    • 길이: 8~20자  
                    • 최소 1개의 대문자(A-Z)  
                    • 최소 1개의 소문자(a-z)  
                    • 최소 1개의 숫자(0-9)  
                    • 최소 1개의 특수문자(!@#$%^&*()_+=-{};:'",.<>/?)
                - 위 조건을 만족하지 않으면 400 응답을 반환합니다.
                - 변경 완료 후, 로그인 화면으로 이동하여 새 비밀번호로 로그인할 수 있습니다.
                """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "비밀번호 변경 성공",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "message": "비밀번호가 변경되었습니다. 다시 로그인해 주세요."
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "비밀번호 정책 위반 또는 잘못된/만료된 토큰",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                        {
                                          "message": "비밀번호는 8~20자, 대소문자/숫자/특수문자를 모두 포함해야 합니다."
                                        }
                                        """)
                            )
                    )
            }
    )
    @PostMapping("/password/reset/commit")
    public ResponseEntity<?> commitReset(@RequestBody @Valid PasswordResetCommitRequest req){
        userService.resetPasswordByToken(req.resetToken(), req.newPassword());
        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다. 다시 로그인해 주세요."));
    }





}