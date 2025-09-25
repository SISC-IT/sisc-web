package org.sejongisc.backend.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collection;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserInfoResponse {
    private UUID id;
    private String name;
    private String email;
    private String phoneNumber;
    private Integer point;
    private String role;                // enum Role을 String으로 변환
    private Collection<?> authorities;  // ROLE_USER, ROLE_ADMIN 등
}