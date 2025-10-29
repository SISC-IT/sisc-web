package org.sejongisc.backend.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import org.sejongisc.backend.auth.dto.LoginRequest;
import org.sejongisc.backend.auth.dto.LoginResponse;

public interface LoginService {
    LoginResponse login(LoginRequest request);

    void logout(String accessToken);
}
