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
import org.sejongisc.backend.user.dto.UserInfoResponse;
import org.sejongisc.backend.user.dto.UserUpdateRequest;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
}
