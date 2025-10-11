package org.sejongisc.backend.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.sejongisc.backend.user.entity.Role;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private UUID userId;
    private String email;
    private String name;
    private Role role;
    private String phoneNumber;
    private Integer point;
}
