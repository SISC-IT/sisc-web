package org.sejongisc.backend.common.auth.springsecurity;

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

    private final UUID uuid;
    private final String name;
    private final String email;
    private final String password;
    private final String phoneNumber;
    private final Role role;
    private final Integer point;

    public CustomUserDetails(User user) {
        this.uuid = user.getUserId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.phoneNumber = user.getPhoneNumber();
        this.role = user.getRole();
        this.point = user.getPoint();
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
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
