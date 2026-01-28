package org.sejongisc.backend.user.service;

import org.sejongisc.backend.auth.dto.SignupRequest;
import org.sejongisc.backend.auth.dto.SignupResponse;
import org.sejongisc.backend.user.dto.UserUpdateRequest;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.auth.oauth.OauthUserInfo;
import org.sejongisc.backend.user.service.projection.UserIdNameProjection;

import java.util.List;
import java.util.UUID;

public interface UserService {
    SignupResponse signUp(SignupRequest dto);

    User findOrCreateUser(OauthUserInfo oauthInfo);

    void updateUser(UUID userId, UserUpdateRequest request);

    User getUserById(UUID userId);

    void deleteUserWithOauth(UUID userId);

    String findEmailByNameAndPhone(String name, String phoneNumber);

    void passwordReset(String email);

    String verifyResetCodeAndIssueToken(String email, String code);

    void resetPasswordByToken(String resetToken, String newPassword);

    User upsertOAuthUser(String provider, String providerId, String email, String name);

    List<UserIdNameProjection> getUserProjectionList();

    List<User> findAllUsersMissingAccount();
}
