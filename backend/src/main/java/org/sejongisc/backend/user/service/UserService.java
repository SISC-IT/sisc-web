package org.sejongisc.backend.user.service;

import org.sejongisc.backend.user.dto.KakaoUserInfoResponse;
import org.sejongisc.backend.user.dto.SignupRequest;
import org.sejongisc.backend.user.dto.SignupResponse;
import org.sejongisc.backend.user.entity.User;

public interface UserService {
    SignupResponse signUp(SignupRequest dto);

    User findOrCreateUser(KakaoUserInfoResponse kakaoInfo);
}
