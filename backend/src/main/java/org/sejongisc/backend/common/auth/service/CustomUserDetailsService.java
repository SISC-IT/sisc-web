package org.sejongisc.backend.common.auth.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.repository.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;


@RequiredArgsConstructor
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        try {
            UUID uuidUserId = UUID.fromString(userId);
            User findUser = userRepository.findById(uuidUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            return new CustomUserDetails(findUser);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
