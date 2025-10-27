package org.sejongisc.backend.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequest {
    private String username;
    private String phoneNumber;
    private String password;  // 변경 시에만 받기
}
