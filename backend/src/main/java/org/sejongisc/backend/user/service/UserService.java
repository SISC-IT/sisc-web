package org.sejongisc.backend.user.service;

import org.sejongisc.backend.user.dto.SignupRequestDto;
import org.sejongisc.backend.user.dto.SignupResponseDto;

public interface UserService {
    SignupResponseDto signUp(SignupRequestDto dto);
}
