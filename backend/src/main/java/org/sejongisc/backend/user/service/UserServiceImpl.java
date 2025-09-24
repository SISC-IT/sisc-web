package org.sejongisc.backend.user.service;

import com.sun.jdi.request.DuplicateRequestException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.dto.SignupRequestDto;
import org.sejongisc.backend.user.dto.SignupResponseDto;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public SignupResponseDto signUp(SignupRequestDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (userRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
            throw new CustomException(ErrorCode.DUPLICATE_PHONE);
        }

        // 패스워드 인코딩
        String encodedPw = passwordEncoder.encode(dto.getPassword());

        Role role = dto.getRole();
        if (role == null) {
            role = Role.TEAM_MEMBER;
        }

        User user = User.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .passwordHash(encodedPw)
                .role(role)
                .phoneNumber(dto.getPhoneNumber())
                .build();

        try {
            User saved = userRepository.save(user);
            return SignupResponseDto.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_USER);
        }

    }

}
