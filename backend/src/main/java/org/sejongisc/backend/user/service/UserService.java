package org.sejongisc.backend.user.service;

import org.sejongisc.backend.auth.dto.SignupRequest;
import org.sejongisc.backend.auth.dto.SignupResponse;
import org.sejongisc.backend.user.dto.UserUpdateRequest;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.auth.oauth.OauthUserInfo;

import java.util.UUID;

public interface UserService {
    SignupResponse signUp(SignupRequest dto);

    User findOrCreateUser(OauthUserInfo oauthInfo);

    void updateUser(UUID userId, UserUpdateRequest request);
}
