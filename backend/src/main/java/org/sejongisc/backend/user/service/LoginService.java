package org.sejongisc.backend.user.service;

import org.sejongisc.backend.user.dto.LoginRequest;
import org.sejongisc.backend.user.dto.LoginResponse;

public interface LoginService {
    LoginResponse login(LoginRequest request);
}
