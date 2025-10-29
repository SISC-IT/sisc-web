package org.sejongisc.backend.common.auth.springsecurity;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
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
        UUID uuid;
        try {
            uuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_ACCESS_TOKEN);
        }
        User findUser = userRepository.findById(uuid).orElseThrow(
                () -> new CustomException(ErrorCode.USER_NOT_FOUND)
        );

        return new CustomUserDetails(findUser);

    }

}
