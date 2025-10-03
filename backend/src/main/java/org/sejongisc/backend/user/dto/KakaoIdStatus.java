package org.sejongisc.backend.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.sejongisc.backend.user.entity.Role;

@Getter
@AllArgsConstructor
public class KakaoIdStatus {
    private Long kakaoId;
    private Role role;
}
