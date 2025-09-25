package org.sejongisc.backend.user.service;

import org.sejongisc.backend.user.dto.SignupRequest;
import org.sejongisc.backend.user.dto.SignupResponse;

public interface UserService {
    SignupResponse signUp(SignupRequest dto);
}
