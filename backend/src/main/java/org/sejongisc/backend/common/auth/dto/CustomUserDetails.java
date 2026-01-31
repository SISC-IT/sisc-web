package org.sejongisc.backend.common.auth.dto;

import lombok.Getter;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class CustomUserDetails implements UserDetails {

    private final UUID userId;
    private final String name;
    private final String email;
    private final String password;          // TODO : 보안을 위해 password 필드 제거 고려
    private final String phoneNumber;       // TODO : 굳이 있어야하나? 제거 고려
    private final Role role;
    private final Integer point;            // TODO : 사용자 포인트는 가변적이기 때문에, 제거 고려

    public CustomUserDetails(User user) {
        this.userId = user.getUserId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.phoneNumber = user.getPhoneNumber();
        this.role = user.getRole();
        this.point = user.getPoint();
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // role.name()이 "PRESIDENT"라면 "ROLE_PRESIDENT"로 변환해서 반환해야 함
        // Spring Security에서는 권한 앞에 "ROLE_" 접두사를 붙이는 것이 관례임
        // hasRole("PRESIDENT") 같은 메서드 호출 시 "ROLE_PRESIDENT"와 매칭되기 때문
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}